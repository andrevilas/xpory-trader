package cpservice

import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper

@Transactional
class TradeReconciliationService {

    GrailsApplication grailsApplication
    NotificationService notificationService

    Map reconcile(Date from, Date to, BigDecimal thresholdRatio = null) {
        Date windowFrom = from ?: defaultWindowFrom()
        Date windowTo = to ?: new Date()
        BigDecimal ratioThreshold = thresholdRatio ?: defaultThresholdRatio()

        Map projection = projectionSettled(windowFrom, windowTo)
        Map telemetry = telemetrySettled(windowFrom, windowTo)

        BigDecimal projectionTotal = projection.total as BigDecimal
        BigDecimal telemetryTotal = telemetry.total as BigDecimal
        int projectionCount = projection.count as int
        int telemetryCount = telemetry.count as int

        BigDecimal amountDiffAbs = (projectionTotal - telemetryTotal).abs()
        int countDiffAbs = Math.abs(projectionCount - telemetryCount)
        BigDecimal base = [projectionTotal.abs(), telemetryTotal.abs(), BigDecimal.ONE].max()
        BigDecimal amountDiffRatio = safeDivide(amountDiffAbs, base)

        boolean diverged = amountDiffRatio > ratioThreshold || countDiffAbs > 0
        String reconciliationKey = "recon:${windowFrom.time}:${windowTo.time}"

        Map result = [
                from           : windowFrom,
                to             : windowTo,
                thresholdRatio : ratioThreshold,
                projection     : projection,
                telemetry      : telemetry,
                amountDiffAbs  : amountDiffAbs,
                amountDiffRatio: amountDiffRatio,
                countDiffAbs   : countDiffAbs,
                diverged       : diverged,
                reconciliationKey: reconciliationKey
        ]

        if (diverged) {
            notificationService?.createTradeReconciliationAlert(result)
        }

        return result
    }

    private Map projectionSettled(Date from, Date to) {
        List<TradeProjection> trades = TradeProjection.list()
        BigDecimal total = BigDecimal.ZERO
        int count = 0
        trades.each { TradeProjection trade ->
            Date ts = trade.occurredAt ?: trade.lastEventTimestamp ?: trade.dateCreated
            if (from && ts && ts.before(from)) {
                return
            }
            if (to && ts && ts.after(to)) {
                return
            }
            String settlementStatus = trade.settlementStatus?.toString()?.toUpperCase()
            String eventName = trade.eventName?.toString()?.toUpperCase()
            boolean settled = settlementStatus == 'SETTLED' || eventName == 'TRADE_SETTLED'
            if (!settled) {
                return
            }
            BigDecimal unitPrice = trade.unitPrice
            BigDecimal qty = trade.confirmedQuantity != null ? new BigDecimal(trade.confirmedQuantity.toString()) :
                    (trade.requestedQuantity != null ? new BigDecimal(trade.requestedQuantity.toString()) : null)
            if (unitPrice != null && qty != null) {
                total += unitPrice * qty
                count++
            }
        }
        [total: total, count: count]
    }

    private Map telemetrySettled(Date from, Date to) {
        List<TelemetryEvent> events = TelemetryEvent.findAllByEventType('TRADER_PURCHASE')
        BigDecimal total = BigDecimal.ZERO
        int count = 0
        events.each { TelemetryEvent event ->
            Date ts = event.eventTimestamp
            if (from && ts && ts.before(from)) {
                return
            }
            if (to && ts && ts.after(to)) {
                return
            }
            Map payload = parsePayload(event.payload)
            String settlementStatus = payload?.settlementStatus?.toString()?.toUpperCase()
            String eventName = payload?.eventName?.toString()?.toUpperCase()
            boolean settled = settlementStatus == 'SETTLED' || eventName == 'TRADE_SETTLED'
            if (!settled) {
                return
            }
            BigDecimal unitPrice = toBigDecimal(payload?.unitPrice)
            BigDecimal confirmedQty = toBigDecimal(payload?.confirmedQuantity)
            BigDecimal requestedQty = toBigDecimal(payload?.requestedQuantity)
            BigDecimal qty = confirmedQty ?: requestedQty
            if (unitPrice != null && qty != null) {
                total += unitPrice * qty
                count++
            }
        }
        [total: total, count: count]
    }

    private Date defaultWindowFrom() {
        long lookbackMinutes = (grailsApplication?.config?.tradeReconciliation?.lookbackMinutes ?: 60) as long
        return new Date(System.currentTimeMillis() - (lookbackMinutes * 60L * 1000L))
    }

    private BigDecimal defaultThresholdRatio() {
        Object raw = grailsApplication?.config?.tradeReconciliation?.thresholdRatio
        if (raw == null) {
            return 0.01G
        }
        try {
            return new BigDecimal(raw.toString())
        } catch (Exception ignored) {
            return 0.01G
        }
    }

    private static Map parsePayload(String raw) {
        if (!raw) {
            return [:]
        }
        try {
            Object parsed = new JsonSlurper().parseText(raw)
            return parsed instanceof Map ? (Map) parsed : [:]
        } catch (Exception ignored) {
            return [:]
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

    private static BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO
        }
        return numerator.divide(denominator, 6, BigDecimal.ROUND_HALF_UP)
    }
}
