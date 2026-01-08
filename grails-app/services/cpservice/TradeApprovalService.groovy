package cpservice

import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.net.HttpURLConnection
import java.math.RoundingMode
import java.security.KeyStore
import java.security.SecureRandom

@Transactional
class TradeApprovalService {

    private static final Logger LOG = LoggerFactory.getLogger(TradeApprovalService)

    GrailsApplication grailsApplication
    PeerTokenService peerTokenService
    JwtService jwtService

    @Transactional(readOnly = true)
    Map listPending(Map params = [:]) {
        Date from = parseDate(params?.from)
        Date to = parseDate(params?.to)
        String wlExporter = params?.wlExporter?.toString()
        String wlImporter = params?.wlImporter?.toString()

        List<TelemetryEvent> events = TelemetryEvent.findAllByEventType('TRADER_PURCHASE')
        Map<String, Map> latestByTrade = [:]

        events.each { TelemetryEvent event ->
            if (from && event.eventTimestamp.before(from)) {
                return
            }
            if (to && event.eventTimestamp.after(to)) {
                return
            }
            Map payload = parsePayload(event.payload)
            if (!payload) {
                return
            }
            if (payload.role?.toString()?.toUpperCase() != 'EXPORTER') {
                return
            }
            if (payload.status?.toString()?.toUpperCase() != 'PENDING') {
                return
            }
            String tradeId = payload.tradeId?.toString()
            if (!tradeId) {
                return
            }
            String originId = payload.originWhiteLabelId?.toString()
            String targetId = payload.targetWhiteLabelId?.toString()
            if (!originId || !targetId) {
                return
            }
            if (wlExporter && wlExporter != originId) {
                return
            }
            if (wlImporter && wlImporter != targetId) {
                return
            }
            Map existing = latestByTrade.get(tradeId)
            if (!existing || (event.eventTimestamp && event.eventTimestamp.after(existing.eventTimestamp as Date))) {
                latestByTrade.put(tradeId, [event: event, payload: payload, eventTimestamp: event.eventTimestamp])
            }
        }

        List<Map> items = latestByTrade.collect { String tradeId, Map entry ->
            Map payload = entry.payload as Map
            [
                    tradeId           : tradeId,
                    externalTradeId   : payload.externalTradeId,
                    originWhiteLabelId: payload.originWhiteLabelId,
                    targetWhiteLabelId: payload.targetWhiteLabelId,
                    originOfferId     : payload.originOfferId,
                    requestedQuantity : payload.requestedQuantity,
                    unitPrice         : payload.unitPrice,
                    totalValue        : resolveTotalValue(payload),
                    status            : payload.status,
                    eventTimestamp    : entry.eventTimestamp,
                    source            : 'telemetry'
            ]
        }.sort { a, b ->
            Date da = a.eventTimestamp as Date
            Date db = b.eventTimestamp as Date
            if (da == null && db == null) return 0
            if (da == null) return 1
            if (db == null) return -1
            return db <=> da
        }

        // remove trades already decided
        Set<String> decidedTradeIds = [] as Set
        if (items) {
            decidedTradeIds = TradeApproval.findAllByTradeIdInList(items.collect { it.tradeId })*.tradeId as Set
        }
        List<Map> filtered = items.findAll { !decidedTradeIds.contains(it.tradeId) }
        Map<String, String> wlNames = resolveWhiteLabelNames(filtered)
        filtered.each { Map item ->
            item.wlExporterName = wlNames[item.originWhiteLabelId?.toString()]
            item.wlImporterName = wlNames[item.targetWhiteLabelId?.toString()]
        }

        return [items: filtered, count: filtered.size()]
    }

    Map decide(String tradeId, String decision, String reason, AdminUser user) {
        if (!tradeId) {
            throw new IllegalArgumentException('tradeId is required')
        }
        if (!decision) {
            throw new IllegalArgumentException('decision is required')
        }
        TradeApproval existing = TradeApproval.findByTradeId(tradeId)
        if (existing) {
            throw new IllegalStateException('trade already decided')
        }
        TelemetryEvent event = findLatestPendingTrade(tradeId, true)
        if (!event) {
            throw new IllegalArgumentException('pending trade not found')
        }
        Map payload = parsePayload(event.payload)
        String originId = payload.originWhiteLabelId?.toString()
        String targetId = payload.targetWhiteLabelId?.toString()
        if (!originId || !targetId) {
            throw new IllegalArgumentException('trade missing wl identifiers')
        }

        TradeApproval approval = new TradeApproval(
                tradeId: tradeId,
                externalTradeId: payload.externalTradeId?.toString(),
                originWhiteLabelId: originId,
                targetWhiteLabelId: targetId,
                decision: decision,
                reason: reason,
                requestedQuantity: safeInt(payload.requestedQuantity),
                unitPrice: safeDecimal(payload.unitPrice),
                currency: payload.currency?.toString() ?: 'X',
                statusBefore: payload.status?.toString()?.toUpperCase() ?: 'PENDING',
                decidedByUserId: user?.id,
                decidedByRole: user?.role
        )
        approval.save(flush: true, failOnError: true)

        Map exporterResult = callExporter(tradeId, originId, targetId, decision, reason)
        approval.statusAfter = exporterResult.statusAfter
        approval.exporterResponseCode = exporterResult.statusCode
        approval.exporterResponseBody = exporterResult.responseBody
        approval.save(flush: true, failOnError: true)

        return [approval: approval, exporter: exporterResult]
    }

    @Transactional(readOnly = true)
    Map getDetails(String tradeId) {
        if (!tradeId) {
            throw new IllegalArgumentException('tradeId is required')
        }
        TelemetryEvent event = findLatestTradeEvent(tradeId, true)
        if (!event) {
            throw new IllegalArgumentException('trade not found')
        }
        Map payload = parsePayload(event.payload)
        String originId = payload.originWhiteLabelId?.toString()
        String targetId = payload.targetWhiteLabelId?.toString()
        if (!originId || !targetId) {
            throw new IllegalArgumentException('trade missing wl identifiers')
        }
        String externalTradeId = payload.externalTradeId?.toString()

        String role = payload.role?.toString()?.toUpperCase()
        String exporterTradeKey = tradeId
        boolean exporterLookupExternal = false
        String importerTradeKey = tradeId
        boolean importerLookupExternal = false
        if (role == 'IMPORTER') {
            exporterTradeKey = externalTradeId ?: tradeId
            exporterLookupExternal = false
            importerTradeKey = tradeId
            importerLookupExternal = false
        } else if (role == 'EXPORTER') {
            exporterTradeKey = tradeId
            exporterLookupExternal = false
            importerTradeKey = externalTradeId ?: tradeId
            importerLookupExternal = externalTradeId ? true : false
        } else {
            importerTradeKey = externalTradeId ?: tradeId
            importerLookupExternal = externalTradeId ? true : false
        }

        Map exporterDetails = [:]
        Map importerDetails = [:]
        try {
            exporterDetails = fetchCoreDetails(originId, exporterTradeKey, exporterLookupExternal)
            if (!exporterDetails && externalTradeId && exporterTradeKey != externalTradeId) {
                exporterDetails = fetchCoreDetails(originId, externalTradeId, true)
            }
        } catch (Exception ex) {
            LOG.warn('Failed to fetch exporter trade details tradeId={} wlId={}', exporterTradeKey, originId, ex)
        }
        try {
            importerDetails = fetchCoreDetails(targetId, importerTradeKey, importerLookupExternal)
            if (!importerDetails && externalTradeId && importerTradeKey != externalTradeId) {
                importerDetails = fetchCoreDetails(targetId, externalTradeId, true)
            }
        } catch (Exception ex) {
            LOG.warn('Failed to fetch importer trade details tradeId={} wlId={}', importerTradeKey, targetId, ex)
        }
        if (!exporterDetails && !importerDetails) {
            LOG.warn('Trade details unavailable for tradeId={}', tradeId)
        }

        Map<String, String> wlNames = resolveWhiteLabelNames([
                [originWhiteLabelId: originId, targetWhiteLabelId: targetId]
        ])

        Map response = [
                tradeId           : tradeId,
                externalTradeId   : externalTradeId,
                originWhiteLabelId: originId,
                targetWhiteLabelId: targetId,
                wlExporterName    : wlNames[originId],
                wlImporterName    : wlNames[targetId],
                offerName         : exporterDetails?.offer?.name ?: importerDetails?.offer?.name
        ]
        response.exporter = buildTradeSide(originId, wlNames[originId], exporterDetails, true)
        response.importer = buildTradeSide(targetId, wlNames[targetId], importerDetails, false)
        return response
    }

    private TelemetryEvent findLatestPendingTrade(String tradeId, boolean allowExternal) {
        List<TelemetryEvent> events = TelemetryEvent.findAllByEventType('TRADER_PURCHASE')
        TelemetryEvent latest = null
        events.each { TelemetryEvent event ->
            Map payload = parsePayload(event.payload)
            if (!payload) {
                return
            }
            if (payload.role?.toString()?.toUpperCase() != 'EXPORTER') {
                return
            }
            if (payload.status?.toString()?.toUpperCase() != 'PENDING') {
                return
            }
            String payloadTradeId = payload.tradeId?.toString()
            String payloadExternalId = payload.externalTradeId?.toString()
            String eventId = event.id?.toString()
            boolean match = payloadTradeId == tradeId
            if (!match && allowExternal) {
                match = payloadExternalId == tradeId
            }
            if (!match && eventId) {
                match = eventId == tradeId
            }
            if (!match) {
                return
            }
            if (!latest || (event.eventTimestamp && event.eventTimestamp.after(latest.eventTimestamp))) {
                latest = event
            }
        }
        return latest
    }

    private TelemetryEvent findLatestTradeEvent(String tradeId, boolean allowExternal) {
        List<TelemetryEvent> events = TelemetryEvent.findAllByEventType('TRADER_PURCHASE')
        TelemetryEvent latest = null
        events.each { TelemetryEvent event ->
            Map payload = parsePayload(event.payload)
            if (!payload) {
                return
            }
            String payloadTradeId = payload.tradeId?.toString()
            String payloadExternalId = payload.externalTradeId?.toString()
            String eventId = event.id?.toString()
            boolean match = payloadTradeId == tradeId
            if (!match && allowExternal) {
                match = payloadExternalId == tradeId
            }
            if (!match && eventId) {
                match = eventId == tradeId
            }
            if (!match) {
                return
            }
            if (!latest || (event.eventTimestamp && event.eventTimestamp.after(latest.eventTimestamp))) {
                latest = event
            }
        }
        return latest
    }

    private Map callExporter(String tradeId, String exporterId, String importerId, String decision, String reason) {
        WhiteLabel exporter = WhiteLabel.get(exporterId)
        if (!exporter || !exporter.gatewayUrl) {
            throw new IllegalArgumentException('exporter gatewayUrl missing')
        }
        String token = peerTokenService.issuePeerToken(importerId, exporterId, ['trader:purchase']).token as String
        String path = decision == TradeApproval.DECISION_APPROVED ?
                "/api/v2/trader/purchases/${tradeId}/approve" :
                "/api/v2/trader/purchases/${tradeId}/reject"
        String endpoint = normalizeUrl(exporter.gatewayUrl, path)
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection()
        if (connection instanceof HttpsURLConnection) {
            SSLContext sslContext = buildSslContext()
            if (sslContext) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.socketFactory)
            }
        }
        connection.requestMethod = 'POST'
        connection.connectTimeout = 5000
        connection.readTimeout = 15000
        connection.doOutput = true
        connection.setRequestProperty('Content-Type', 'application/json')
        connection.setRequestProperty('Authorization', "Bearer ${token}")

        Map body = [:]
        if (decision == TradeApproval.DECISION_REJECTED && reason) {
            body.reason = reason
        }
        if (body) {
            connection.outputStream.withWriter('UTF-8') { it << JsonOutput.toJson(body) }
        }

        int status = connection.responseCode
        String responseText = status < 400 ? connection.inputStream?.getText('UTF-8') : connection.errorStream?.getText('UTF-8')
        connection.disconnect()
        String statusAfter = decision == TradeApproval.DECISION_APPROVED ? 'CONFIRMED' : 'REJECTED'
        return [statusCode: status, responseBody: responseText, statusAfter: statusAfter]
    }

    private Map fetchCoreDetails(String whiteLabelId, String tradeId, boolean lookupExternal) {
        if (!whiteLabelId || !tradeId) {
            return [:]
        }
        WhiteLabel whiteLabel = WhiteLabel.get(whiteLabelId)
        if (!whiteLabel || !whiteLabel.gatewayUrl) {
            throw new IllegalArgumentException('whiteLabel gatewayUrl missing')
        }
        String token = jwtService.issueToken(whiteLabelId, [])
        String path = "/api/v2/control-plane/trader/purchases/${tradeId}/details"
        String endpoint = normalizeUrl(whiteLabel.gatewayUrl, path)
        if (lookupExternal) {
            endpoint = endpoint + '?lookup=external'
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection()
        if (connection instanceof HttpsURLConnection) {
            SSLContext sslContext = buildSslContext()
            if (sslContext) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.socketFactory)
            }
        }
        connection.requestMethod = 'GET'
        connection.connectTimeout = 5000
        connection.readTimeout = 15000
        connection.setRequestProperty('Accept', 'application/json')
        connection.setRequestProperty('Authorization', "Bearer ${token}")

        int status = connection.responseCode
        String responseText = status < 400 ? connection.inputStream?.getText('UTF-8') : connection.errorStream?.getText('UTF-8')
        connection.disconnect()
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("core trade details failed with status ${status}")
        }
        responseText ? (new JsonSlurper().parseText(responseText) as Map) : [:]
    }

    private static Map buildTradeSide(String wlId, String wlName, Map details, boolean exporter) {
        Map side = [
                wlId  : wlId,
                wlName: wlName
        ]
        Map actor = exporter ? (details?.provider as Map) : (details?.buyer as Map)
        if (actor) {
            side.clientName = actor.name
            side.clientPhone = resolvePhone(actor)
        }
        Object price = exporter ? (details?.unitPrice ?: details?.offer?.price) : (details?.purchase?.unitPrice ?: details?.purchase?.value ?: details?.unitPrice)
        if (price != null) {
            side.price = price
        }
        side
    }

    private static String resolvePhone(Map actor) {
        if (!actor) {
            return null
        }
        actor.cell?.toString() ?: actor.phone?.toString()
    }

    private static Map parsePayload(String payload) {
        if (!payload) {
            return null
        }
        try {
            Object parsed = new JsonSlurper().parseText(payload)
            return parsed instanceof Map ? (Map) parsed : null
        } catch (Exception ignored) {
            return null
        }
    }

    private static BigDecimal resolveTotalValue(Map payload) {
        if (!payload) {
            return null
        }
        BigDecimal total = safeDecimal(payload.totalPrice ?: payload.totalValue ?: payload.amount)
        if (total != null) {
            return total
        }
        BigDecimal unit = safeDecimal(payload.unitPrice)
        Integer qty = safeInt(payload.requestedQuantity ?: payload.confirmedQuantity)
        if (unit == null || qty == null) {
            return null
        }
        unit.multiply(new BigDecimal(qty)).setScale(2, RoundingMode.HALF_UP)
    }

    private static Map<String, String> resolveWhiteLabelNames(Collection<Map> items) {
        if (!items) {
            return [:]
        }
        Set<String> ids = items.collectMany { Map item ->
            [item.originWhiteLabelId?.toString(), item.targetWhiteLabelId?.toString()]
        }.findAll { it } as Set
        if (!ids) {
            return [:]
        }
        WhiteLabel.findAllByIdInList(ids.toList()).collectEntries { WhiteLabel wl ->
            [(wl.id): wl.name]
        }
    }

    private static Date parseDate(Object value) {
        if (!value) {
            return null
        }
        if (value instanceof Date) {
            return value
        }
        try {
            return Date.from(java.time.OffsetDateTime.parse(value.toString()).toInstant())
        } catch (Exception ignored) {
            return null
        }
    }

    private static Integer safeInt(Object value) {
        if (value == null) {
            return null
        }
        try {
            return Integer.parseInt(value.toString())
        } catch (Exception ignored) {
            return null
        }
    }

    private static BigDecimal safeDecimal(Object value) {
        if (value == null) {
            return null
        }
        try {
            return new BigDecimal(value.toString())
        } catch (Exception ignored) {
            return null
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
}
