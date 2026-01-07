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
        boolean pairFilter = wlExporter && wlImporter
        boolean includeOrphans = params?.includeOrphans?.toString()?.toBoolean()
        List<String> whiteLabelIds = includeOrphans ? [] : WhiteLabel.list().collect { it.id }
        Closure criteriaFilters = {
            if (wlId) {
                or {
                    eq('sourceId', wlId)
                    eq('targetId', wlId)
                }
            }
            if (pairFilter) {
                or {
                    and {
                        eq('sourceId', wlExporter)
                        eq('targetId', wlImporter)
                    }
                    and {
                        eq('sourceId', wlImporter)
                        eq('targetId', wlExporter)
                    }
                }
            } else {
                if (wlExporter) {
                    eq('sourceId', wlExporter)
                }
                if (wlImporter) {
                    eq('targetId', wlImporter)
                }
            }
            if (statusFilter) {
                eq('status', statusFilter)
            }
        }

        List<Relationship> relationshipsForTotals = Relationship.createCriteria().list {
            criteriaFilters.delegate = delegate
            criteriaFilters()
            order('lastUpdated', 'desc')
        }
        relationshipsForTotals = relationshipsForTotals.findAll { Relationship rel ->
            isUuid(rel.sourceId) && isUuid(rel.targetId) &&
                    (includeOrphans || (whiteLabelIds.contains(rel.sourceId) && whiteLabelIds.contains(rel.targetId)))
        }
        Map<String, Map> tradeStatsByPair = buildTradeStats(from, to, wlId, wlImporter, wlExporter, pairFilter)
        if (!includeOrphans) {
            if (whiteLabelIds) {
                tradeStatsByPair = tradeStatsByPair.findAll { String key, Map value ->
                    List<String> parts = key?.split('::', 2) as List<String>
                    parts?.size() == 2 && whiteLabelIds.contains(parts[0]) && whiteLabelIds.contains(parts[1])
                }
            } else {
                tradeStatsByPair = [:]
            }
        }
        Map<String, Map> pairs = buildPairEntries(relationshipsForTotals)
        pairs.values().each { Map entry ->
            String wlAId = entry.wlAId?.toString()
            String wlBId = entry.wlBId?.toString()
            Map aToBStats = normalizeTradeStats(tradeStatsByPair.get(pairKey(wlAId, wlBId)))
            Map bToAStats = normalizeTradeStats(tradeStatsByPair.get(pairKey(wlBId, wlAId)))
            entry.tradeMetrics = [
                    aToB: aToBStats,
                    bToA: bToAStats
            ]
            BigDecimal totalExported = confirmedTotal(aToBStats)
            BigDecimal totalImported = confirmedTotal(bToAStats)
            entry.totals = [
                    totalExported: totalExported,
                    totalImported: totalImported,
                    balance: totalExported - totalImported
            ]
            entry.lastTradeAt = maxDate(aToBStats.lastTradeAt as Date, bToAStats.lastTradeAt as Date)
        }
        List<Map> pairList = pairs.values().toList().sort { Map left, Map right ->
            Date leftDate = left.lastUpdated ?: left.lastTradeAt
            Date rightDate = right.lastUpdated ?: right.lastTradeAt
            if (leftDate == rightDate) {
                return 0
            }
            if (leftDate == null) {
                return 1
            }
            if (rightDate == null) {
                return -1
            }
            return rightDate <=> leftDate
        }
        Number total = pairList.size()
        List<Map> relationshipsPage = pairList.drop(offset).take(limit)
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
                relationships: relationshipsPage
        ]
    }

    private boolean isUuid(String value) {
        if (!value) {
            return false
        }
        value ==~ /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/
    }

    private Map<String, Map> buildTradeStats(Date from, Date to, String wlId, String wlImporter, String wlExporter, boolean pairFilter) {
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
            if (pairFilter) {
                boolean matchesPair = (wlExporter == sourceId && wlImporter == targetId) ||
                        (wlExporter == targetId && wlImporter == sourceId)
                if (!matchesPair) {
                    return
                }
            } else {
                if (wlExporter && wlExporter != sourceId) {
                    return
                }
                if (wlImporter && wlImporter != targetId) {
                    return
                }
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

    private Map<String, Map> buildPairEntries(List<Relationship> relationships) {
        Map<String, Map> pairs = [:]
        relationships.each { Relationship rel ->
            String wlAId = canonicalFirst(rel.sourceId, rel.targetId)
            String wlBId = canonicalSecond(rel.sourceId, rel.targetId)
            String key = pairKey(wlAId, wlBId)
            Map entry = pairs.get(key)
            if (!entry) {
                entry = [
                        wlAId       : wlAId,
                        wlBId       : wlBId,
                        availability: [hasAToB: false, hasBToA: false],
                        relationships: [aToB: null, bToA: null],
                        lastUpdated : null
                ]
                pairs.put(key, entry)
            }
            Map relSnapshot = relationshipSnapshot(rel)
            boolean aToB = rel.sourceId == wlAId && rel.targetId == wlBId
            if (aToB) {
                entry.relationships.aToB = relSnapshot
                entry.availability.hasAToB = true
            } else {
                entry.relationships.bToA = relSnapshot
                entry.availability.hasBToA = true
            }
            Date relUpdatedAt = rel.lastUpdated
            if (!entry.lastUpdated || (relUpdatedAt && relUpdatedAt.after(entry.lastUpdated as Date))) {
                entry.lastUpdated = relUpdatedAt
            }
        }
        return pairs
    }

    private Map relationshipSnapshot(Relationship rel) {
        [
                sourceId   : rel.sourceId,
                targetId   : rel.targetId,
                fxRate     : rel.fxRate,
                limitAmount: rel.limitAmount,
                status     : rel.status,
                updatedAt  : rel.lastUpdated
        ]
    }

    private String canonicalFirst(String first, String second) {
        if (first == null) {
            return second
        }
        if (second == null) {
            return first
        }
        return first <= second ? first : second
    }

    private String canonicalSecond(String first, String second) {
        if (first == null) {
            return second
        }
        if (second == null) {
            return first
        }
        return first <= second ? second : first
    }

    private BigDecimal confirmedTotal(Map stats) {
        if (!stats) {
            return BigDecimal.ZERO
        }
        Map totals = stats.totals instanceof Map ? (Map) stats.totals : [:]
        Object value = totals.CONFIRMED
        if (value instanceof BigDecimal) {
            return (BigDecimal) value
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString())
        }
        return BigDecimal.ZERO
    }

    private Date maxDate(Date left, Date right) {
        if (left == null) {
            return right
        }
        if (right == null) {
            return left
        }
        return left.after(right) ? left : right
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
