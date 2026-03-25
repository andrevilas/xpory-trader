package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class TradeProjectionServiceSpec extends Specification implements ServiceUnitTest<TradeProjectionService>, DataTest {

    def setupSpec() {
        mockDomains TradeProjection, TelemetryEvent
    }

    void "upsertFromTelemetry creates and updates projection for same external trade"() {
        given:
        Date pendingTs = new Date(1712000000000L)
        Date settledTs = new Date(1712003600000L)
        TelemetryEvent pending = new TelemetryEvent(
                whiteLabelId: 'wl-imp',
                nodeId: 'wl-imp',
                eventType: 'TRADER_PURCHASE',
                payload: '{}',
                eventTimestamp: pendingTs
        ).save(validate: false, flush: true)

        TelemetryEvent settled = new TelemetryEvent(
                whiteLabelId: 'wl-exp',
                nodeId: 'wl-exp',
                eventType: 'TRADER_PURCHASE',
                payload: '{}',
                eventTimestamp: settledTs
        ).save(validate: false, flush: true)

        when:
        service.upsertFromTelemetry(pending, [
                externalTradeId   : 'ext-1',
                tradeId           : 'trade-1',
                originWhiteLabelId: 'wl-exp',
                targetWhiteLabelId: 'wl-imp',
                status            : 'PENDING',
                approvalMode      : 'HYBRID',
                pendingReason     : 'FIRST_TRADE',
                eventName         : 'TRADE_PENDING',
                unitPrice         : 10G,
                requestedQuantity : 2,
                currency          : 'X',
                idempotencyKey    : 'idem-1',
                executedAt        : pendingTs.time
        ])
        TradeProjection updated = service.upsertFromTelemetry(settled, [
                externalTradeId   : 'ext-1',
                status            : 'CONFIRMED',
                settlementStatus  : 'SETTLED',
                eventName         : 'TRADE_SETTLED',
                confirmedQuantity : 2,
                settlementAt      : settledTs.time
        ])

        then:
        TradeProjection.count() == 1
        updated.tradeExternalId == 'ext-1'
        updated.status == 'CONFIRMED'
        updated.settlementStatus == 'SETTLED'
        updated.approvalMode == 'HYBRID'
        updated.pendingReason == 'FIRST_TRADE'
        updated.settledAt?.time == settledTs.time
        updated.confirmedQuantity == 2
        updated.requestedQuantity == 2
        updated.idempotencyKey == 'idem-1'
    }
}
