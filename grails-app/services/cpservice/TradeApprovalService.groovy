package cpservice

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.net.HttpURLConnection

@Transactional
class TradeApprovalService {

    private static final Logger LOG = LoggerFactory.getLogger(TradeApprovalService)

    PeerTokenService peerTokenService

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
        Set<String> decidedTradeIds = TradeApproval.findAllByTradeIdInList(items.collect { it.tradeId })*.tradeId as Set
        List<Map> filtered = items.findAll { !decidedTradeIds.contains(it.tradeId) }

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
        TelemetryEvent event = findLatestPendingTrade(tradeId)
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

    private TelemetryEvent findLatestPendingTrade(String tradeId) {
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
            if (payload.tradeId?.toString() != tradeId) {
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
}
