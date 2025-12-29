package cpservice

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Persist telemetry events in the append-only cp_telemetry log.
 */
@Transactional
class TelemetryIngestService {

    TraderAccountService traderAccountService
    TradeMetricsService tradeMetricsService

    List<TelemetryEvent> ingest(Collection<Map> events) {
        if (!events) {
            return []
        }
        events.collect { Map event ->
            TelemetryEvent stored = persistEvent(event)
            traderAccountService?.processTelemetry(stored, event)
            recordTradeMetrics(event)
            stored
        }
    }

    protected TelemetryEvent persistEvent(Map eventPayload) {
        String whiteLabelId = eventPayload.whiteLabelId
        if (!whiteLabelId) {
            throw new IllegalArgumentException('whiteLabelId is required on telemetry events')
        }
        String nodeId = eventPayload.nodeId ?: 'unknown'
        String eventType = eventPayload.eventType ?: 'unspecified'
        Date eventTimestamp = coerceTimestamp(eventPayload.eventTimestamp)
        String payload = serialisePayload(eventPayload.payload ?: [:])

        def telemetryEvent = new TelemetryEvent(
                whiteLabelId: whiteLabelId,
                nodeId: nodeId,
                eventType: eventType,
                payload: payload,
                eventTimestamp: eventTimestamp
        )

        telemetryEvent.save(flush: true, failOnError: true)
        return telemetryEvent
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
}
