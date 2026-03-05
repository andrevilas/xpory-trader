package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class TelemetryIngestServiceSpec extends Specification implements ServiceUnitTest<TelemetryIngestService>, DataTest {

    def setupSpec() {
        mockDomains TelemetryEvent
    }

    void "ingest deduplicates TRADER_PURCHASE by idempotency fingerprint"() {
        given:
        service.traderAccountService = Mock(TraderAccountService)
        service.tradeMetricsService = Mock(TradeMetricsService)
        service.notificationService = Mock(NotificationService)
        service.tradeProjectionService = Mock(TradeProjectionService)

        Date occurredAt = new Date(1710000000000L)
        Map event = [
                whiteLabelId : 'wl-1',
                nodeId       : 'node-1',
                eventType    : 'TRADER_PURCHASE',
                eventTimestamp: occurredAt,
                payload      : [
                        eventName         : 'TRADE_SETTLED',
                        status            : 'CONFIRMED',
                        idempotencyKey    : 'idem-100',
                        externalTradeId   : 'trade-ext-100',
                        originWhiteLabelId: 'wl-exp',
                        targetWhiteLabelId: 'wl-imp',
                        unitPrice         : 10G,
                        confirmedQuantity : 2,
                        settlementAt      : occurredAt.time
                ]
        ]

        when:
        List<TelemetryEvent> stored = service.ingest([event, event])

        then:
        stored.size() == 2
        stored[0].id == stored[1].id
        TelemetryEvent.count() == 1
        TelemetryEvent.first().idempotencyKey == 'idem-100'
        TelemetryEvent.first().dedupeFingerprint
        1 * service.traderAccountService.processTelemetry(_, _)
        1 * service.tradeMetricsService.recordTradeStatus('CONFIRMED')
        1 * service.tradeProjectionService.upsertFromTelemetry(_, _)
        1 * service.notificationService.processTradeTelemetry(_, _)
    }

    void "ingest builds fallback idempotency key for TRADER_PURCHASE without idempotencyKey"() {
        given:
        service.traderAccountService = Mock(TraderAccountService)
        service.tradeMetricsService = Mock(TradeMetricsService)
        service.notificationService = Mock(NotificationService)
        service.tradeProjectionService = Mock(TradeProjectionService)

        Date executedAt = new Date(1711111111000L)
        Map event = [
                whiteLabelId : 'wl-1',
                nodeId       : 'node-1',
                eventType    : 'TRADER_PURCHASE',
                eventTimestamp: new Date(1711111111999L),
                payload      : [
                        eventName         : 'TRADE_CONFIRMED',
                        status            : 'CONFIRMED',
                        externalTradeId   : 'trade-ext-fallback',
                        originWhiteLabelId: 'wl-exp',
                        targetWhiteLabelId: 'wl-imp',
                        unitPrice         : 12.5G,
                        confirmedQuantity : 1,
                        executedAt        : executedAt.time
                ]
        ]

        when:
        service.ingest([event])

        then:
        TelemetryEvent.count() == 1
        TelemetryEvent.first().idempotencyKey == "fallback:trade-ext-fallback:TRADE_CONFIRMED:${executedAt.time}"
        TelemetryEvent.first().dedupeFingerprint
    }
}
