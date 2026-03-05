package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import groovy.json.JsonOutput
import spock.lang.Specification

class TradeReconciliationServiceSpec extends Specification implements ServiceUnitTest<TradeReconciliationService>, DataTest {

    def setupSpec() {
        mockDomains TradeProjection, TelemetryEvent
    }

    void "reconcile does not alert when projection and telemetry are aligned"() {
        given:
        service.notificationService = Mock(NotificationService)
        Date ts = new Date(1713000000000L)
        new TradeProjection(
                tradeExternalId: 'ext-1',
                originWhiteLabelId: 'wl-exp',
                targetWhiteLabelId: 'wl-imp',
                status: 'CONFIRMED',
                settlementStatus: 'SETTLED',
                eventName: 'TRADE_SETTLED',
                unitPrice: 10G,
                confirmedQuantity: 2,
                occurredAt: ts,
                lastEventTimestamp: ts
        ).save(validate: false, flush: true)
        new TelemetryEvent(
                whiteLabelId: 'wl-imp',
                nodeId: 'wl-imp',
                eventType: 'TRADER_PURCHASE',
                eventTimestamp: ts,
                payload: JsonOutput.toJson([
                        eventName         : 'TRADE_SETTLED',
                        settlementStatus  : 'SETTLED',
                        originWhiteLabelId: 'wl-exp',
                        targetWhiteLabelId: 'wl-imp',
                        unitPrice         : 10G,
                        confirmedQuantity : 2
                ])
        ).save(validate: false, flush: true)

        when:
        Map result = service.reconcile(new Date(ts.time - 1000), new Date(ts.time + 1000), 0.01G)

        then:
        !result.diverged
        result.amountDiffAbs == 0G
        result.countDiffAbs == 0
        0 * service.notificationService.createTradeReconciliationAlert(_)
    }

    void "reconcile alerts when divergence exceeds threshold"() {
        given:
        service.notificationService = Mock(NotificationService)
        Date ts = new Date(1713000000000L)
        new TradeProjection(
                tradeExternalId: 'ext-2',
                originWhiteLabelId: 'wl-exp',
                targetWhiteLabelId: 'wl-imp',
                status: 'CONFIRMED',
                settlementStatus: 'SETTLED',
                eventName: 'TRADE_SETTLED',
                unitPrice: 10G,
                confirmedQuantity: 3,
                occurredAt: ts,
                lastEventTimestamp: ts
        ).save(validate: false, flush: true)
        new TelemetryEvent(
                whiteLabelId: 'wl-imp',
                nodeId: 'wl-imp',
                eventType: 'TRADER_PURCHASE',
                eventTimestamp: ts,
                payload: JsonOutput.toJson([
                        eventName         : 'TRADE_SETTLED',
                        settlementStatus  : 'SETTLED',
                        originWhiteLabelId: 'wl-exp',
                        targetWhiteLabelId: 'wl-imp',
                        unitPrice         : 10G,
                        confirmedQuantity : 2
                ])
        ).save(validate: false, flush: true)

        when:
        Map result = service.reconcile(new Date(ts.time - 1000), new Date(ts.time + 1000), 0.01G)

        then:
        result.diverged
        result.amountDiffAbs == 10G
        result.countDiffAbs == 0
        1 * service.notificationService.createTradeReconciliationAlert({ Map payload ->
            payload.diverged == true && payload.amountDiffAbs == 10G
        })
    }
}
