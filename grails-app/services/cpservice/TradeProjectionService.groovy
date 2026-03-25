package cpservice

import grails.gorm.transactions.Transactional
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Transactional
class TradeProjectionService {

    TradeProjection upsertFromTelemetry(TelemetryEvent event, Map payload) {
        if (!event || !payload) {
            return null
        }
        String tradeExternalId = (payload.externalTradeId ?: payload.tradeId)?.toString()
        if (!tradeExternalId) {
            return null
        }

        TradeProjection projection = TradeProjection.findByTradeExternalId(tradeExternalId) ?: new TradeProjection(tradeExternalId: tradeExternalId)
        projection.tradeId = (payload.tradeId ?: projection.tradeId)?.toString()
        projection.originWhiteLabelId = (payload.originWhiteLabelId ?: projection.originWhiteLabelId)?.toString()
        projection.targetWhiteLabelId = (payload.targetWhiteLabelId ?: projection.targetWhiteLabelId)?.toString()
        projection.status = payload.status?.toString()?.toUpperCase() ?: projection.status
        projection.settlementStatus = payload.settlementStatus?.toString()?.toUpperCase() ?: projection.settlementStatus
        projection.eventName = payload.eventName?.toString()?.toUpperCase() ?: projection.eventName
        projection.approvalMode = payload.approvalMode?.toString()?.toUpperCase() ?: projection.approvalMode
        projection.approvalPath = payload.approvalPath?.toString()?.toUpperCase() ?: projection.approvalPath
        projection.pendingReason = payload.pendingReason?.toString()?.toUpperCase() ?: projection.pendingReason
        projection.unitPrice = toBigDecimal(payload.unitPrice) ?: projection.unitPrice
        projection.requestedQuantity = toInteger(payload.requestedQuantity) ?: projection.requestedQuantity
        projection.confirmedQuantity = toInteger(payload.confirmedQuantity) ?: projection.confirmedQuantity
        projection.currency = payload.currency?.toString() ?: projection.currency ?: 'X'
        projection.idempotencyKey = payload.idempotencyKey?.toString() ?: projection.idempotencyKey
        projection.occurredAt = resolveOccurredAt(payload, event.eventTimestamp) ?: projection.occurredAt
        projection.lastEventTimestamp = event.eventTimestamp ?: projection.lastEventTimestamp

        if (projection.status == 'CONFIRMED' && !projection.confirmedAt) {
            projection.confirmedAt = projection.occurredAt ?: event.eventTimestamp
        }
        if ((projection.settlementStatus == 'SETTLED' || projection.eventName == 'TRADE_SETTLED') && !projection.settledAt) {
            projection.settledAt = projection.occurredAt ?: event.eventTimestamp
        }
        if ((projection.settlementStatus == 'REFUNDED' || projection.eventName == 'TRADE_REFUNDED') && !projection.refundedAt) {
            projection.refundedAt = projection.occurredAt ?: event.eventTimestamp
        }

        projection.save(flush: true, failOnError: true)
        return projection
    }

    private static Date resolveOccurredAt(Map payload, Date fallback) {
        Object value = payload?.settlementAt ?: payload?.executedAt ?: payload?.expiresAt ?: payload?.confirmedAt ?: payload?.createdAt
        if (value instanceof Number) {
            return new Date(((Number) value).longValue())
        }
        if (value instanceof Date) {
            return (Date) value
        }
        if (value instanceof CharSequence) {
            try {
                return Date.from(OffsetDateTime.parse(value.toString()).toInstant())
            } catch (DateTimeParseException ignored) {
                return fallback
            }
        }
        return fallback
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null
        }
        if (value instanceof Number) {
            return ((Number) value).intValue()
        }
        try {
            return Integer.valueOf(value.toString())
        } catch (Exception ignored) {
            return null
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString())
        }
        try {
            return new BigDecimal(value.toString())
        } catch (Exception ignored) {
            return null
        }
    }
}
