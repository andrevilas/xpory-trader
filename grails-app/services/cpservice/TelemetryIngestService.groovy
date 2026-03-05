package cpservice

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Persist telemetry events in the append-only cp_telemetry log.
 */
@Transactional
class TelemetryIngestService {

    TraderAccountService traderAccountService
    TradeMetricsService tradeMetricsService
    NotificationService notificationService
    TradeProjectionService tradeProjectionService

    List<TelemetryEvent> ingest(Collection<Map> events) {
        if (!events) {
            return []
        }
        events.collect { Map event ->
            Map persisted = persistEvent(event)
            TelemetryEvent stored = persisted.event as TelemetryEvent
            boolean duplicate = persisted.duplicate == true
            if (!duplicate) {
                traderAccountService?.processTelemetry(stored, event)
                recordTradeMetrics(event)
                if (event?.eventType == 'TRADER_PURCHASE') {
                    Map payload = (event.payload instanceof Map) ? (Map) event.payload : [:]
                    tradeProjectionService?.upsertFromTelemetry(stored, payload)
                    notificationService?.processTradeTelemetry(stored, payload)
                }
            }
            stored
        }
    }

    protected Map persistEvent(Map eventPayload) {
        String whiteLabelId = eventPayload.whiteLabelId
        if (!whiteLabelId) {
            throw new IllegalArgumentException('whiteLabelId is required on telemetry events')
        }
        String nodeId = eventPayload.nodeId ?: 'unknown'
        String eventType = eventPayload.eventType ?: 'unspecified'
        Map payloadMap = (eventPayload.payload instanceof Map) ? (Map) eventPayload.payload : [:]
        validateTradePayload(eventType, payloadMap)
        Date eventTimestamp = coerceTimestamp(eventPayload.eventTimestamp)
        String payload = serialisePayload(payloadMap)
        String idempotencyKey = resolveIdempotencyKey(eventType, payloadMap, eventTimestamp)
        String dedupeFingerprint = buildDedupeFingerprint(eventType, whiteLabelId, nodeId, idempotencyKey, payloadMap, eventTimestamp)

        if (dedupeFingerprint) {
            TelemetryEvent existing = TelemetryEvent.findByDedupeFingerprint(dedupeFingerprint)
            if (existing) {
                return [event: existing, duplicate: true]
            }
        }

        def telemetryEvent = new TelemetryEvent(
                whiteLabelId: whiteLabelId,
                nodeId: nodeId,
                eventType: eventType,
                idempotencyKey: idempotencyKey,
                dedupeFingerprint: dedupeFingerprint,
                payload: payload,
                eventTimestamp: eventTimestamp
        )

        telemetryEvent.save(flush: true, failOnError: true)
        return [event: telemetryEvent, duplicate: false]
    }

    private Date coerceTimestamp(Object ts) {
        if (ts instanceof Date) {
            return (Date) ts
        }
        if (ts instanceof Number) {
            return new Date(((Number) ts).longValue())
        }
        if (ts instanceof CharSequence) {
            try {
                return Date.from(OffsetDateTime.parse(ts.toString()).toInstant())
            } catch (DateTimeParseException ignored) {
                // fallback handled below
            }
        }
        return new Date()
    }

    private String serialisePayload(Object payload) {
        if (payload instanceof CharSequence) {
            return payload.toString()
        }
        return JsonOutput.toJson(payload ?: [:])
    }

    private static String resolveIdempotencyKey(String eventType, Map payload, Date eventTimestamp) {
        if (payload?.idempotencyKey) {
            return payload.idempotencyKey.toString()
        }
        if (eventType != 'TRADER_PURCHASE') {
            return null
        }
        String tradeExternalId = (payload?.externalTradeId ?: payload?.tradeId ?: 'unknown').toString()
        String eventName = (payload?.eventName ?: payload?.status ?: 'TRADE_STATUS').toString()
        Long occurredAt = coerceOccurredAt(payload, eventTimestamp)
        return "fallback:${tradeExternalId}:${eventName}:${occurredAt}".toString()
    }

    private static String buildDedupeFingerprint(String eventType, String whiteLabelId, String nodeId, String idempotencyKey, Map payload, Date eventTimestamp) {
        if (eventType != 'TRADER_PURCHASE') {
            return null
        }
        String eventName = (payload?.eventName ?: payload?.status ?: 'TRADE_STATUS').toString()
        Long occurredAt = coerceOccurredAt(payload, eventTimestamp)
        String tradeExternalId = (payload?.externalTradeId ?: payload?.tradeId ?: 'unknown').toString()
        String base = [eventType, whiteLabelId ?: 'unknown', nodeId ?: 'unknown', tradeExternalId, eventName, occurredAt.toString(), idempotencyKey ?: 'none'].join('|')
        return sha256Hex(base)
    }

    private static Long coerceOccurredAt(Map payload, Date eventTimestamp) {
        Object value = payload?.settlementAt ?: payload?.executedAt ?: payload?.expiresAt ?: payload?.confirmedAt ?: payload?.createdAt
        if (value instanceof Number) {
            return ((Number) value).longValue()
        }
        if (value instanceof Date) {
            return ((Date) value).time
        }
        if (value instanceof CharSequence) {
            try {
                return OffsetDateTime.parse(value.toString()).toInstant().toEpochMilli()
            } catch (DateTimeParseException ignored) {
                // fallback below
            }
        }
        return eventTimestamp?.time ?: 0L
    }

    private static String sha256Hex(String value) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        byte[] hash = digest.digest(value.getBytes('UTF-8'))
        hash.collect { String.format('%02x', it) }.join('')
    }

    private void recordTradeMetrics(Map eventPayload) {
        if (!eventPayload) {
            return
        }
        String eventType = eventPayload.eventType ?: 'unspecified'
        if (eventType != 'TRADER_PURCHASE') {
            return
        }
        Map payload = (eventPayload.payload instanceof Map) ? (Map) eventPayload.payload : [:]
        String status = payload.status?.toString()?.toUpperCase() ?: 'UNKNOWN'
        tradeMetricsService?.recordTradeStatus(status)
    }

    private void validateTradePayload(String eventType, Object payload) {
        if (eventType != 'TRADER_PURCHASE') {
            return
        }
        if (!(payload instanceof Map)) {
            throw new IllegalArgumentException('TRADER_PURCHASE payload must be an object')
        }
        Map payloadMap = (Map) payload
        List<String> missing = []
        if (!payloadMap.originWhiteLabelId) {
            missing << 'originWhiteLabelId'
        }
        if (!payloadMap.targetWhiteLabelId) {
            missing << 'targetWhiteLabelId'
        }
        if (payloadMap.unitPrice == null) {
            missing << 'unitPrice'
        }
        if (payloadMap.requestedQuantity == null && payloadMap.confirmedQuantity == null) {
            missing << 'requestedQuantity/confirmedQuantity'
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("TRADER_PURCHASE payload missing fields: ${missing.join(', ')}")
        }
    }
}
