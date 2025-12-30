package cpservice

import grails.core.GrailsApplication
import groovy.json.JsonOutput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID

class ImbalanceDispatchService {

    private static final Logger LOG = LoggerFactory.getLogger(ImbalanceDispatchService)
    private static final Logger INTEGRATION_LOG = LoggerFactory.getLogger('cpservice.integration')

    static transactional = false

    GrailsApplication grailsApplication
    JwtService jwtService
    ImbalanceService imbalanceService

    ImbalanceSignal dispatch(ImbalanceSignal signal) {
        if (!signal) {
            return null
        }
        WhiteLabel target = WhiteLabel.get(signal.targetId)
        if (!target) {
            return markFailed(signal, "Unknown target WL ${signal.targetId}")
        }
        if (!target.gatewayUrl) {
            return markFailed(signal, "Missing gatewayUrl for WL ${signal.targetId}")
        }

        int maxAttempts = (grailsApplication?.config?.imbalance?.dispatch?.maxAttempts ?: 3) as int
        int backoffMillis = (grailsApplication?.config?.imbalance?.dispatch?.backoffMillis ?: 500) as int
        int connectTimeout = (grailsApplication?.config?.imbalance?.dispatch?.connectTimeoutMillis ?: 5000) as int
        int readTimeout = (grailsApplication?.config?.imbalance?.dispatch?.readTimeoutMillis ?: 15000) as int

        Map payload = [
                sourceId      : signal.sourceId,
                targetId      : signal.targetId,
                action        : signal.action,
                reason        : signal.reason,
                initiatedBy   : signal.initiatedBy,
                effectiveFrom : formatDate(signal.effectiveFrom),
                effectiveUntil: formatDate(signal.effectiveUntil)
        ]

        String endpoint = normalizeUrl(target.gatewayUrl, '/api/v2/control-plane/imbalance/signals')
        String correlationId = MDC.get('correlationId')
        boolean injectedCorrelation = false
        if (!correlationId) {
            correlationId = UUID.randomUUID().toString()
            MDC.put('correlationId', correlationId)
            injectedCorrelation = true
        }
        INTEGRATION_LOG.info('CP->WL dispatch start signalId={} sourceId={} targetId={} endpoint={} attempts={}',
                signal.id, signal.sourceId, signal.targetId, endpoint, maxAttempts)
        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                signal.dispatchAttempts = attempt
                signal.lastDispatchAt = new Date()
                try {
                    String token = jwtService.issueToken(target.id, [])
                    int status = postJson(endpoint, payload, token, connectTimeout, readTimeout, correlationId)
                    INTEGRATION_LOG.info('CP->WL dispatch response signalId={} status={} attempt={} endpoint={}',
                            signal.id, status, attempt, endpoint)
                    if (status >= 200 && status < 300) {
                        signal.dispatchStatus = 'sent'
                        if (!signal.dispatchedAt) {
                            signal.dispatchedAt = new Date()
                        }
                        signal.dispatchError = null
                        signal.save(flush: true, failOnError: true)
                        INTEGRATION_LOG.info('CP->WL dispatch acknowledged signalId={} targetId={} attempt={}',
                                signal.id, signal.targetId, attempt)
                        return imbalanceService.acknowledge(signal.id, 'dispatch')
                    }
                    signal.dispatchError = "HTTP ${status}"
                    LOG.warn('Imbalance dispatch attempt {} failed (status={}, signalId={})', attempt, status, signal.id)
                } catch (Exception ex) {
                    signal.dispatchError = ex.message?.toString()?.take(500)
                    LOG.warn('Imbalance dispatch attempt {} failed (signalId={}): {}', attempt, signal.id, ex.message)
                }
                signal.dispatchStatus = (attempt >= maxAttempts) ? 'failed' : 'pending'
                signal.save(flush: true, failOnError: true)
                if (attempt < maxAttempts) {
                    sleep(backoffMillis * attempt)
                }
            }
            INTEGRATION_LOG.info('CP->WL dispatch exhausted signalId={} targetId={} attempts={} error={}',
                    signal.id, signal.targetId, signal.dispatchAttempts, signal.dispatchError)
            signal
        } finally {
            if (injectedCorrelation) {
                MDC.remove('correlationId')
            }
        }
    }

    private ImbalanceSignal markFailed(ImbalanceSignal signal, String reason) {
        signal.dispatchStatus = 'failed'
        signal.dispatchError = reason?.take(500)
        signal.lastDispatchAt = new Date()
        signal.save(flush: true, failOnError: true)
        signal
    }

    private int postJson(String url, Map payload, String token, int connectTimeout, int readTimeout, String correlationId) {
        URL targetUrl = new URL(url)
        HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection()
        if (connection instanceof javax.net.ssl.HttpsURLConnection) {
            SSLContext sslContext = buildSslContext()
            if (sslContext != null) {
                ((javax.net.ssl.HttpsURLConnection) connection).setSSLSocketFactory(sslContext.socketFactory)
            }
        }
        connection.setRequestMethod('POST')
        connection.setConnectTimeout(connectTimeout)
        connection.setReadTimeout(readTimeout)
        connection.setDoOutput(true)
        connection.setRequestProperty('Content-Type', 'application/json')
        if (token) {
            connection.setRequestProperty('Authorization', "Bearer ${token}")
        }
        if (correlationId) {
            connection.setRequestProperty('X-Correlation-Id', correlationId)
        }

        String json = JsonOutput.toJson(payload)
        connection.outputStream.withWriter('UTF-8') { writer ->
            writer << json
        }

        int status = connection.responseCode
        try {
            connection.inputStream?.close()
        } catch (IOException ignored) {
            // ignore non-2xx response bodies
        }
        connection.errorStream?.close()
        connection.disconnect()
        return status
    }

    private SSLContext buildSslContext() {
        def cfg = grailsApplication?.config?.security?.mtls ?: [:]
        boolean enabled = cfg?.enabled in [true, 'true']
        if (!enabled) {
            return null
        }
        String keyStorePath = cfg?.keyStore?.toString()
        String keyStorePassword = cfg?.keyStorePassword?.toString()
        String trustStorePath = cfg?.trustStore?.toString()
        String trustStorePassword = cfg?.trustStorePassword?.toString()
        if (!keyStorePath || !keyStorePassword || !trustStorePath || !trustStorePassword) {
            return null
        }

        KeyStore keyStore = KeyStore.getInstance(resolveKeyStoreType(cfg?.keyStoreType))
        keyStore.load(new FileInputStream(keyStorePath), keyStorePassword.toCharArray())
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.defaultAlgorithm)
        kmf.init(keyStore, keyStorePassword.toCharArray())

        KeyStore trustStore = KeyStore.getInstance(resolveKeyStoreType(cfg?.trustStoreType))
        trustStore.load(new FileInputStream(trustStorePath), trustStorePassword.toCharArray())
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.defaultAlgorithm)
        tmf.init(trustStore)

        SSLContext context = SSLContext.getInstance('TLS')
        context.init(kmf.keyManagers, tmf.trustManagers, new SecureRandom())
        return context
    }

    private String resolveKeyStoreType(Object raw) {
        return raw?.toString() ?: 'PKCS12'
    }

    private String normalizeUrl(String baseUrl, String path) {
        String trimmedBase = baseUrl?.trim()
        if (!trimmedBase) {
            return path
        }
        if (trimmedBase.endsWith('/') && path.startsWith('/')) {
            return trimmedBase.substring(0, trimmedBase.length() - 1) + path
        }
        if (!trimmedBase.endsWith('/') && !path.startsWith('/')) {
            return trimmedBase + '/' + path
        }
        return trimmedBase + path
    }

    private String formatDate(Date value) {
        if (!value) {
            return null
        }
        return value.toInstant().atOffset(java.time.ZoneOffset.UTC).toString()
    }
}
