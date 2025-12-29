package cpservice

import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper
import java.math.BigDecimal

@Transactional(readOnly = true)
class ReportService {

    Map tradeBalanceSummary() {
        List<Relationship> relationships = Relationship.list()
        Map<String, Map> tradeStatsByPair = buildTradeStats()
        BigDecimal totalLimit = relationships.inject(BigDecimal.ZERO) { acc, rel ->
            acc + (rel.limitAmount ?: BigDecimal.ZERO)
        }
        Map<String, List<Relationship>> byStatus = relationships.groupBy { it.status ?: 'unknown' }
        Map<String, Integer> statusCounts = byStatus.collectEntries { k, v -> [k, v.size()] }
        Map<String, Integer> tradeStatusTotals = aggregateTradeStatusTotals(tradeStatsByPair)

        [
                generatedAt : new Date(),
                totals      : [
                        relationships: relationships.size(),
                        active       : statusCounts.get('active', 0),
                        blocked      : statusCounts.get('blocked', 0),
                        inactive     : statusCounts.get('inactive', 0),
                        totalLimit   : totalLimit,
                        tradeStatusTotals: tradeStatusTotals
                ],
                relationships: relationships.collect { rel ->
                    Map tradeStats = tradeStatsByPair.get(pairKey(rel.sourceId, rel.targetId)) ?: [:]
                    [
                            sourceId   : rel.sourceId,
                            targetId   : rel.targetId,
                            fxRate     : rel.fxRate,
                            limitAmount: rel.limitAmount,
                            status     : rel.status,
                            updatedAt  : rel.lastUpdated,
                            tradeMetrics: tradeStats
                    ]
                }
        ]
    }

    private Map<String, Map> buildTradeStats() {
        List<TelemetryEvent> events = TelemetryEvent.findAllByEventType('TRADER_PURCHASE')
        Map<String, Map> stats = [:]
        events.each { TelemetryEvent event ->
            Map payload = parsePayload(event.payload)
            String sourceId = payload.originWhiteLabelId?.toString()
            String targetId = payload.targetWhiteLabelId?.toString()
            if (!sourceId || !targetId) {
                return
            }
            String status = payload.status?.toString()?.toUpperCase() ?: 'UNKNOWN'
            BigDecimal unitPrice = toBigDecimal(payload.unitPrice)
            BigDecimal requestedQty = toBigDecimal(payload.requestedQuantity)
            BigDecimal confirmedQty = toBigDecimal(payload.confirmedQuantity)
            BigDecimal qty = (status == 'CONFIRMED' && confirmedQty != null) ? confirmedQty : requestedQty

            String key = pairKey(sourceId, targetId)
            Map entry = stats.get(key)
            if (!entry) {
                entry = [
                        counts: [CONFIRMED: 0, PENDING: 0, REJECTED: 0, UNKNOWN: 0],
                        totals: [CONFIRMED: BigDecimal.ZERO, PENDING: BigDecimal.ZERO, REJECTED: BigDecimal.ZERO, UNKNOWN: BigDecimal.ZERO],
                        lastTradeAt: null
                ]
                stats.put(key, entry)
            }

            entry.counts[status] = (entry.counts[status] ?: 0) + 1
            if (unitPrice != null && qty != null) {
                entry.totals[status] = (entry.totals[status] ?: BigDecimal.ZERO) + (unitPrice * qty)
            }
            Date ts = event.eventTimestamp
            if (!entry.lastTradeAt || (ts && ts.after(entry.lastTradeAt as Date))) {
                entry.lastTradeAt = ts
            }
        }
        return stats
    }

    private Map<String, Integer> aggregateTradeStatusTotals(Map<String, Map> statsByPair) {
        Map<String, Integer> totals = [CONFIRMED: 0, PENDING: 0, REJECTED: 0, UNKNOWN: 0]
        statsByPair.values().each { Map stat ->
            Map counts = stat.counts ?: [:]
            totals.keySet().each { String key ->
                totals[key] = (totals[key] ?: 0) + (counts[key] ?: 0)
            }
        }
        return totals
    }

    private Map parsePayload(String payload) {
        if (!payload) {
            return [:]
        }
        try {
            Object parsed = new JsonSlurper().parseText(payload)
            return parsed instanceof Map ? (Map) parsed : [:]
        } catch (Exception ignored) {
            return [:]
        }
    }

    private BigDecimal toBigDecimal(Object value) {
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

    private String pairKey(String sourceId, String targetId) {
        return "${sourceId}::${targetId}"
    }
}
