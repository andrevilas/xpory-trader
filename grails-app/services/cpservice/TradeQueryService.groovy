package cpservice

import grails.gorm.transactions.Transactional

import java.time.OffsetDateTime

@Transactional(readOnly = true)
class TradeQueryService {

    TradeApprovalService tradeApprovalService

    Map list(Map params = [:]) {
        Integer limit = parseInt(params?.limit, 50)
        Integer offset = parseInt(params?.offset, 0)
        limit = Math.min(limit, 200)

        Date from = parseDate(params?.from ?: params?.dateStart ?: params?.dateStarts)
        Date to = parseDate(params?.to ?: params?.dateEnd ?: params?.dateEnds)
        String sourceId = (params?.sourceId ?: params?.wlExporter)?.toString()
        String targetId = (params?.targetId ?: params?.wlImporter)?.toString()
        String statusFilter = params?.status?.toString()?.toUpperCase()
        String clientNameFilter = params?.clientName?.toString()?.trim()?.toLowerCase()
        BigDecimal minValue = toBigDecimal(params?.minValue ?: params?.valueMin)
        BigDecimal maxValue = toBigDecimal(params?.maxValue ?: params?.valueMax)

        List<TradeProjection> projections = TradeProjection.createCriteria().list {
            if (from) {
                ge('lastEventTimestamp', from)
            }
            if (to) {
                le('lastEventTimestamp', to)
            }
            if (sourceId) {
                eq('originWhiteLabelId', sourceId)
            }
            if (targetId) {
                eq('targetWhiteLabelId', targetId)
            }
            if (statusFilter) {
                eq('status', statusFilter)
            }
            order('lastEventTimestamp', 'desc')
            order('dateCreated', 'desc')
        }

        Map<String, String> nameById = resolveWhiteLabelNames(projections.collect { TradeProjection projection ->
            [wlExporter: projection.originWhiteLabelId, wlImporter: projection.targetWhiteLabelId]
        })

        List<Map> trades = projections.collect { TradeProjection projection ->
            Map trade = buildTrade(projection)
            enrichTrade(trade, nameById)
            trade
        }.findAll { Map trade ->
            matchesOperationalFilters(trade, clientNameFilter, minValue, maxValue)
        }

        int total = trades.size()
        int safeOffset = Math.min(Math.max(offset, 0), total)
        int safeLimit = Math.max(limit, 1)
        List<Map> page = trades.drop(safeOffset).take(safeLimit)

        return [
                items : page,
                count : total,
                limit : safeLimit,
                offset: safeOffset
        ]
    }

    private Map buildTrade(TradeProjection projection) {
        if (!projection) {
            return null
        }
        String wlExporter = projection.originWhiteLabelId?.toString()
        String wlImporter = projection.targetWhiteLabelId?.toString()
        if (!wlExporter || !wlImporter) {
            return null
        }
        String status = projection.status?.toString() ?: 'UNKNOWN'
        BigDecimal unitPrice = toBigDecimal(projection.unitPrice)
        BigDecimal requestedQty = toBigDecimal(projection.requestedQuantity)
        BigDecimal confirmedQty = toBigDecimal(projection.confirmedQuantity)
        BigDecimal qty = (status?.toUpperCase() == 'CONFIRMED' && confirmedQty != null) ? confirmedQty : requestedQty
        BigDecimal totalValue = (unitPrice != null && qty != null) ? unitPrice * qty : null
        String tradeId = projection.tradeId?.toString() ?: projection.tradeExternalId?.toString() ?: projection.id?.toString()

        return [
                tradeId           : tradeId,
                tradeExternalId   : projection.tradeExternalId,
                wlExporter        : wlExporter,
                wlImporter        : wlImporter,
                status            : status,
                createdAt         : projection.occurredAt ?: projection.lastEventTimestamp ?: projection.dateCreated,
                unitPrice         : unitPrice,
                requestedQuantity : requestedQty,
                confirmedQuantity : confirmedQty,
                totalValue        : totalValue,
                approvalMode      : projection.approvalMode,
                approvalPath      : projection.approvalPath,
                pendingReason     : projection.pendingReason,
                settlementStatus  : projection.settlementStatus
        ]
    }

    private void enrichTrade(Map trade, Map<String, String> nameById) {
        if (!trade) {
            return
        }
        trade.wlExporterName = nameById.get(trade.wlExporter)
        trade.wlImporterName = nameById.get(trade.wlImporter)

        try {
            Map details = tradeApprovalService?.getDetails((trade.tradeExternalId ?: trade.tradeId)?.toString())
            if (!details) {
                return
            }
            Map exporter = details.exporter instanceof Map ? (Map) details.exporter : [:]
            Map importer = details.importer instanceof Map ? (Map) details.importer : [:]

            trade.offerName = details.offerName ?: details.offer?.name
            trade.exporterClientName = exporter.clientName
            trade.exporterClientPhone = exporter.clientPhone
            trade.importerClientName = importer.clientName
            trade.importerClientPhone = importer.clientPhone
            trade.exporterPrice = toBigDecimal(exporter.price)
            trade.importerPrice = toBigDecimal(importer.priceWithoutFx ?: importer.priceOriginal ?: importer.price)
            trade.importerFinalPrice = toBigDecimal(importer.price)
            trade.exporterTotalValue = computeOperationalTotal(trade.exporterPrice, trade)
            trade.importerTotalValue = computeOperationalTotal(trade.importerFinalPrice ?: trade.importerPrice, trade)
            trade.originOfferId = details.originOfferId ?: details.offer?.id ?: trade.originOfferId
            trade.originalOfferUrl = buildOriginalOfferUrl(trade.wlExporter?.toString(), trade.originOfferId?.toString())
        } catch (Exception ignored) {
            // Listing must stay resilient even when some WL detail endpoints are unavailable.
        }
    }

    private boolean matchesOperationalFilters(Map trade, String clientNameFilter, BigDecimal minValue, BigDecimal maxValue) {
        if (!trade) {
            return false
        }
        if (clientNameFilter) {
            String exporterName = trade.exporterClientName?.toString()?.toLowerCase()
            String importerName = trade.importerClientName?.toString()?.toLowerCase()
            if (!(exporterName?.contains(clientNameFilter) || importerName?.contains(clientNameFilter))) {
                return false
            }
        }
        if (minValue != null || maxValue != null) {
            List<BigDecimal> candidates = [
                    toBigDecimal(trade.exporterTotalValue),
                    toBigDecimal(trade.importerTotalValue),
                    toBigDecimal(trade.totalValue)
            ].findAll { it != null } as List<BigDecimal>
            if (!candidates) {
                return false
            }
            boolean matchesValue = candidates.any { BigDecimal value ->
                if (minValue != null && value < minValue) {
                    return false
                }
                if (maxValue != null && value > maxValue) {
                    return false
                }
                return true
            }
            if (!matchesValue) {
                return false
            }
        }
        return true
    }

    private BigDecimal computeOperationalTotal(BigDecimal price, Map trade) {
        if (price == null) {
            return null
        }
        BigDecimal qty = toBigDecimal(trade.confirmedQuantity ?: trade.requestedQuantity)
        if (qty == null) {
            return null
        }
        return price.multiply(qty)
    }

    private String buildOriginalOfferUrl(String whiteLabelId, String offerId) {
        if (!whiteLabelId || !offerId) {
            return null
        }
        WhiteLabel exporter = WhiteLabel.get(whiteLabelId)
        if (!exporter?.gatewayUrl) {
            return null
        }
        String base = exporter.gatewayUrl.toString().replaceAll('/+$', '')
        return "${base}/troca/oferta/${offerId}"
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
