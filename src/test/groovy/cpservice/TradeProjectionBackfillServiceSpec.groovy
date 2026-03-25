package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import groovy.json.JsonOutput
import spock.lang.Specification

class TradeProjectionBackfillServiceSpec extends Specification implements ServiceUnitTest<TradeProjectionBackfillService>, DataTest {

    def setupSpec() {
        mockDomains TelemetryEvent, TradeProjection
    }

    void "rebuild projects trader purchase events into cp_trades"() {
        given:
        service.tradeProjectionService = new TradeProjectionService()
        Date now = new Date()
        new TelemetryEvent(
            whiteLabelId: 'xpory',
            nodeId: 'node-1',
            eventType: 'TRADER_PURCHASE',
            payload: JsonOutput.toJson([
                externalTradeId   : 'trade-ext-1',
                tradeId           : 'trade-1',
                originWhiteLabelId: 'clubedatroca',
                targetWhiteLabelId: 'xpory',
                status            : 'PENDING',
                unitPrice         : 1500G,
                requestedQuantity : 1,
                eventName         : 'TRADE_PENDING'
            ]),
            eventTimestamp: now
        ).save(flush: true, failOnError: true)

        when:
        Map result = service.rebuild()

        then:
        result.scanned == 1
        result.projected == 1
        result.skipped == 0
        result.totalTrades == 1

        and:
        TradeProjection projection = TradeProjection.findByTradeExternalId('trade-ext-1')
        projection
        projection.tradeId == 'trade-1'
        projection.originWhiteLabelId == 'clubedatroca'
        projection.targetWhiteLabelId == 'xpory'
        projection.status == 'PENDING'
    }

    void "rebuild skips telemetry without trade identity"() {
        given:
        service.tradeProjectionService = new TradeProjectionService()
        new TelemetryEvent(
            whiteLabelId: 'xpory',
            nodeId: 'node-1',
            eventType: 'TRADER_PURCHASE',
            payload: JsonOutput.toJson([
                originWhiteLabelId: 'clubedatroca',
                targetWhiteLabelId: 'xpory',
                status: 'PENDING',
                unitPrice: 1500G,
                requestedQuantity: 1
            ]),
            eventTimestamp: new Date()
        ).save(flush: true, failOnError: true)

        when:
        Map result = service.rebuild()

        then:
        result.scanned == 1
        result.projected == 0
        result.skipped == 1
        result.totalTrades == 0
    }
}
