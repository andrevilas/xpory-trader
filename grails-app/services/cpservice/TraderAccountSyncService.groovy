package cpservice

import grails.core.GrailsApplication
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus

import java.net.HttpURLConnection
import java.util.UUID

class TraderAccountSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(TraderAccountSyncService)
    private static final Logger INTEGRATION_LOG = LoggerFactory.getLogger('cpservice.integration')

    GrailsApplication grailsApplication
    JwtService jwtService
    TraderAccountService traderAccountService

    Map syncFromWhiteLabel(String whiteLabelId, String adminId = null, String correlationId = null) {
        WhiteLabel whiteLabel = WhiteLabel.get(whiteLabelId)
        if (!whiteLabel) {
            return [status: HttpStatus.NOT_FOUND.value(), body: [ok: false, error: 'Unknown white label']]
        }
        if (!whiteLabel.gatewayUrl) {
            return [status: HttpStatus.BAD_REQUEST.value(), body: [ok: false, error: 'Missing gatewayUrl']]
        }

        String endpoint = normalizeUrl(whiteLabel.gatewayUrl, '/api/v2/control-plane/trader-account')
        boolean injectedCorrelation = false
        if (!correlationId) {
            correlationId = UUID.randomUUID().toString()
            MDC.put('correlationId', correlationId)
            injectedCorrelation = true
        }

        INTEGRATION_LOG.info('CP->WL trader sync start wlId={} endpoint={}', whiteLabelId, endpoint)
        try {
            String token = jwtService.issueToken(whiteLabel.id, [])
            HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection()
            connection.requestMethod = 'GET'
            connection.connectTimeout = (grailsApplication?.config?.controlPlane?.connectTimeoutMillis ?: 5000) as int
            connection.readTimeout = (grailsApplication?.config?.controlPlane?.readTimeoutMillis ?: 15000) as int
            connection.setRequestProperty('Accept', 'application/json')
            connection.setRequestProperty('Authorization', "Bearer ${token}")
            if (correlationId) {
                connection.setRequestProperty('X-Correlation-Id', correlationId)
            }

            int status = connection.responseCode
            String responseText = status < 400 ? connection.inputStream?.getText('UTF-8') : connection.errorStream?.getText('UTF-8')
            connection.disconnect()
            INTEGRATION_LOG.info('CP->WL trader sync response wlId={} status={} endpoint={}', whiteLabelId, status, endpoint)

            if (status == HttpStatus.NOT_FOUND.value()) {
                INTEGRATION_LOG.info('CP->WL trader sync not found wlId={} status={} endpoint={}', whiteLabelId, status, endpoint)
                return [status: status, body: [ok: false, error: 'Trader account not found on WL']]
            }
            if (status < 200 || status >= 300) {
                LOG.warn('Trader sync failed wlId={} status={} body={}', whiteLabelId, status, responseText)
                INTEGRATION_LOG.info('CP->WL trader sync failed wlId={} status={} endpoint={}', whiteLabelId, status, endpoint)
                return [status: status, body: [ok: false, error: "WL responded with status ${status}"]]
            }

            Map payload = responseText ? (new JsonSlurper().parseText(responseText) as Map) : [:]
            if (!payload?.id) {
                return [status: HttpStatus.UNPROCESSABLE_ENTITY.value(), body: [ok: false, error: 'WL payload missing id']]
            }

            TraderAccount trader = traderAccountService.upsert(whiteLabel, payload, adminId)
            INTEGRATION_LOG.info('CP->WL trader sync complete wlId={} traderId={} status={}',
                    whiteLabelId, trader?.id, status)
            return [status: HttpStatus.OK.value(), body: [ok: true, traderId: trader?.id, wlId: whiteLabelId]]
        } finally {
            if (injectedCorrelation) {
                MDC.remove('correlationId')
            }
        }
    }

    private static String normalizeUrl(String baseUrl, String path) {
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
}
