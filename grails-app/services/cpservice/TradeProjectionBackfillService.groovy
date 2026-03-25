package cpservice

import grails.gorm.transactions.Transactional

@Transactional
class TradeProjectionBackfillService {

    TradeProjectionService tradeProjectionService

    Map rebuild(Map options = [:]) {
        Integer max = coerceInteger(options.max)
        Date from = options.from instanceof Date ? (Date) options.from : null
        Date to = options.to instanceof Date ? (Date) options.to : null

        List<TelemetryEvent> events = TelemetryEvent.createCriteria().list {
            eq('eventType', 'TRADER_PURCHASE')
            if (from) {
                ge('eventTimestamp', from)
            }
            if (to) {
                le('eventTimestamp', to)
            }
            order('eventTimestamp', 'asc')
            if (max) {
                maxResults(max)
            }
        } as List<TelemetryEvent>

        int scanned = 0
        int projected = 0
        int skipped = 0

        events.each { TelemetryEvent event ->
            scanned++
            Map payload = parsePayload(event?.payload)
            if (!payload) {
                skipped++
                return
            }
            TradeProjection projection = tradeProjectionService?.upsertFromTelemetry(event, payload)
            if (projection) {
                projected++
            } else {
                skipped++
            }
        }

        [
            scanned   : scanned,
            projected : projected,
            skipped   : skipped,
            totalTrades: TradeProjection.count()
        ]
    }

    private static Map parsePayload(String raw) {
        if (!raw) {
            return null
        }
        def parsed = grails.converters.JSON.parse(raw)
        return parsed instanceof Map ? (Map) parsed : null
    }

    private static Integer coerceInteger(Object value) {
        if (value == null) {
            return null
        }
        try {
            Integer parsed = Integer.valueOf(value.toString())
            return parsed > 0 ? parsed : null
        } catch (Exception ignored) {
            return null
        }
    }
}
