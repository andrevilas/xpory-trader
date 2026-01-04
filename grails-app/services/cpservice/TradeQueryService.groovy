package cpservice

import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper

import java.time.OffsetDateTime

@Transactional(readOnly = true)
class TradeQueryService {

    Map list(Map params = [:]) {
        Integer limit = parseInt(params?.limit, 50)
        Integer offset = parseInt(params?.offset, 0)
        limit = Math.min(limit, 200)

        Date from = parseDate(params?.from ?: params?.dateStart ?: params?.dateStarts)
        Date to = parseDate(params?.to ?: params?.dateEnd ?: params?.dateEnds)
        String sourceId = (params?.sourceId ?: params?.wlExporter)?.toString()
        String targetId = (params?.targetId ?: params?.wlImporter)?.toString()
        String statusFilter = params?.status?.toString()?.toUpperCase()

        List<TelemetryEvent> events = TelemetryEvent.createCriteria().list {
            eq('eventType', 'TRADER_PURCHASE')
            if (from) {
                ge('eventTimestamp', from)
            }
            if (to) {
                le('eventTimestamp', to)
            }
            order('eventTimestamp', 'desc')
        }

        List<Map> trades = events.collect { TelemetryEvent event ->
            Map payload = parsePayload(event.payload)
            Map trade = buildTrade(payload, event)
            return trade
        }.findAll { Map trade ->
            if (!trade) {
                return false
            }
            if (sourceId && trade.wlExporter != sourceId) {
                return false
            }
            if (targetId && trade.wlImporter != targetId) {
                return false
            }
            if (statusFilter && (trade.status ?: '').toUpperCase() != statusFilter) {
                return false
            }
            return true
        }

        int total = trades.size()
        int safeOffset = Math.min(Math.max(offset, 0), total)
        int safeLimit = Math.max(limit, 1)
        List<Map> page = trades.drop(safeOffset).take(safeLimit)

        Map<String, String> nameById = resolveWhiteLabelNames(page)
        page.each { Map trade ->
            trade.wlExporterName = nameById.get(trade.wlExporter)
            trade.wlImporterName = nameById.get(trade.wlImporter)
        }

        return [
                items : page,
                count : total,
                limit : safeLimit,
                offset: safeOffset
        ]
    }

    private Map buildTrade(Map payload, TelemetryEvent event) {
        if (!payload) {
            return null
        }
        String wlExporter = payload.originWhiteLabelId?.toString()
        String wlImporter = payload.targetWhiteLabelId?.toString()
        if (!wlExporter || !wlImporter) {
            return null
        }
        String status = payload.status?.toString() ?: 'UNKNOWN'
        BigDecimal unitPrice = toBigDecimal(payload.unitPrice)
        BigDecimal requestedQty = toBigDecimal(payload.requestedQuantity)
        BigDecimal confirmedQty = toBigDecimal(payload.confirmedQuantity)
        BigDecimal qty = (status?.toUpperCase() == 'CONFIRMED' && confirmedQty != null) ? confirmedQty : requestedQty
        BigDecimal totalValue = (unitPrice != null && qty != null) ? unitPrice * qty : null
        String tradeId = payload.tradeId?.toString() ?: payload.externalTradeId?.toString() ?: event.id?.toString()

        return [
                tradeId           : tradeId,
                wlExporter        : wlExporter,
                wlImporter        : wlImporter,
                status            : status,
                createdAt         : event.eventTimestamp,
                unitPrice         : unitPrice,
                requestedQuantity : requestedQty,
                confirmedQuantity : confirmedQty,
                totalValue        : totalValue
        ]
    }

    private Map<String, String> resolveWhiteLabelNames(List<Map> trades) {
        Set<String> ids = new HashSet<>()
        trades.each { Map trade ->
            if (trade.wlExporter) ids.add(trade.wlExporter.toString())
            if (trade.wlImporter) ids.add(trade.wlImporter.toString())
        }
        if (ids.isEmpty()) {
            return [:]
        }
        List<WhiteLabel> labels = WhiteLabel.findAllByIdInList(ids.toList())
        return labels.collectEntries { WhiteLabel wl -> [(wl.id): wl.name] }
    }

    private static Map parsePayload(String payload) {
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

    private static Integer parseInt(Object value, int fallback) {
        if (value == null) {
            return fallback
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
            return Date.from(OffsetDateTime.parse(value.toString()).toInstant())
        } catch (Exception ignored) {
            return null
        }
    }
}
