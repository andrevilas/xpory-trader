package cpservice

import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper
import java.math.BigDecimal

@Transactional(readOnly = true)
class ReportService {

    Map tradeBalanceSummary(Map params = [:]) {
        Date from = parseDate(params?.from)
        Date to = parseDate(params?.to)
        String wlId = params?.wlId?.toString()
        String wlImporter = params?.wlImporter?.toString()
        String wlExporter = params?.wlExporter?.toString()
        String status = params?.status?.toString()
        Integer limit = parseInt(params?.limit, 100)
        Integer offset = parseInt(params?.offset, 0)
        limit = Math.min(limit, 200)
        String statusFilter = status ? status.toLowerCase() : null

        Closure criteriaFilters = {
            if (wlId) {
                or {
                    eq('sourceId', wlId)
                    eq('targetId', wlId)
                }
            }
            if (wlExporter) {
                eq('sourceId', wlExporter)
            }
            if (wlImporter) {
                eq('targetId', wlImporter)
            }
            if (statusFilter) {
                eq('status', statusFilter)
            }
        }

        List<Relationship> relationshipsForTotals = Relationship.createCriteria().list {
            criteriaFilters.delegate = delegate
            criteriaFilters()
        }
        Number total = Relationship.createCriteria().count {
            criteriaFilters.delegate = delegate
            criteriaFilters()
        }
        List<Relationship> relationshipsPage = Relationship.createCriteria().list(max: limit, offset: offset) {
            criteriaFilters.delegate = delegate
            criteriaFilters()
            order('lastUpdated', 'desc')
        }

        Map<String, Map> tradeStatsByPair = buildTradeStats(from, to, wlId, wlImporter, wlExporter)
        BigDecimal totalLimit = relationshipsForTotals.inject(BigDecimal.ZERO) { acc, rel ->
            acc + (rel.limitAmount ?: BigDecimal.ZERO)
        }
        Map<String, List<Relationship>> byStatus = relationshipsForTotals.groupBy { it.status ?: 'unknown' }
        Map<String, Integer> statusCounts = byStatus.collectEntries { k, v -> [k, v.size()] }
        Map<String, Integer> tradeStatusTotals = aggregateTradeStatusTotals(tradeStatsByPair)

        [
                generatedAt : new Date(),
                filters     : [
                        from       : from,
                        to         : to,
                        wlId       : wlId,
                        wlImporter : wlImporter,
                        wlExporter : wlExporter
                ],
                totals      : [
                        relationships: total,
                        active       : statusCounts.get('active', 0),
                        blocked      : statusCounts.get('blocked', 0),
                        inactive     : statusCounts.get('inactive', 0),
                        totalLimit   : totalLimit,
                        tradeStatusTotals: tradeStatusTotals
                ],
                count       : total,
                relationships: relationshipsPage.collect { rel ->
                    Map tradeStats = normalizeTradeStats(tradeStatsByPair.get(pairKey(rel.sourceId, rel.targetId)))
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

    private Map<String, Map> buildTradeStats(Date from, Date to, String wlId, String wlImporter, String wlExporter) {
        List<TelemetryEvent> events = TelemetryEvent.findAllByEventType('TRADER_PURCHASE')
        Map<String, Map> stats = [:]
        events.each { TelemetryEvent event ->
            if (from && event.eventTimestamp.before(from)) {
                return
            }
            if (to && event.eventTimestamp.after(to)) {
                return
            }
            Map payload = parsePayload(event.payload)
            String sourceId = payload.originWhiteLabelId?.toString()
            String targetId = payload.targetWhiteLabelId?.toString()
            if (!sourceId || !targetId) {
                return
            }
            if (wlId && !(wlId == sourceId || wlId == targetId)) {
                return
            }
            if (wlExporter && wlExporter != sourceId) {
                return
            }
            if (wlImporter && wlImporter != targetId) {
                return
            }
            String status = payload.status?.toString()?.toUpperCase() ?: 'UNKNOWN'
            String eventName = payload.eventName?.toString()?.toUpperCase()
            String settlementStatus = payload.settlementStatus?.toString()?.toUpperCase()
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
                        settled: [count: 0, total: BigDecimal.ZERO],
                        lastTradeAt: null
                ]
                stats.put(key, entry)
            }

            if (!(entry.settled instanceof Map)) {
                entry.settled = [count: 0, total: BigDecimal.ZERO]
            }

            entry.counts[status] = (entry.counts[status] ?: 0) + 1
            if (unitPrice != null && qty != null) {
                entry.totals[status] = (entry.totals[status] ?: BigDecimal.ZERO) + (unitPrice * qty)
            }
            boolean settledEvent = eventName == 'TRADE_SETTLED' || settlementStatus == 'SETTLED'
            if (settledEvent && unitPrice != null && qty != null) {
                entry.settled.count = (entry.settled.count ?: 0) + 1
                entry.settled.total = (entry.settled.total ?: BigDecimal.ZERO) + (unitPrice * qty)
            }
            Date ts = event.eventTimestamp
            if (!entry.lastTradeAt || (ts && ts.after(entry.lastTradeAt as Date))) {
                entry.lastTradeAt = ts
            }
        }
        return stats
    }

    private Map emptyTradeStats() {
        [
                counts     : [CONFIRMED: 0, PENDING: 0, REJECTED: 0, UNKNOWN: 0],
                totals     : [CONFIRMED: BigDecimal.ZERO, PENDING: BigDecimal.ZERO, REJECTED: BigDecimal.ZERO, UNKNOWN: BigDecimal.ZERO],
                settled    : [count: 0, total: BigDecimal.ZERO],
                lastTradeAt: null
        ]
    }

    private Map normalizeTradeStats(Map raw) {
        Map normalized = emptyTradeStats()
        if (!raw) {
            return normalized
        }
        if (raw.counts instanceof Map) {
            normalized.counts.putAll(raw.counts as Map)
        }
        if (raw.totals instanceof Map) {
            normalized.totals.putAll(raw.totals as Map)
        }
        if (raw.settled instanceof Map) {
            normalized.settled.putAll(raw.settled as Map)
        }
        normalized.lastTradeAt = raw.lastTradeAt
        return normalized
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

    private Integer parseInt(Object value, Integer fallback) {
        if (value == null) {
            return fallback
        }
        if (value instanceof Number) {
            return ((Number) value).intValue()
        }
        try {
            return Integer.parseInt(value.toString())
        } catch (Exception ignored) {
            return fallback
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
}
