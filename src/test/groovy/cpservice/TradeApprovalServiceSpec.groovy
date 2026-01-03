package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import groovy.json.JsonOutput
import spock.lang.Specification

class TradeApprovalServiceSpec extends Specification implements ServiceUnitTest<TradeApprovalService>, DataTest {

    def setupSpec() {
        mockDomains TelemetryEvent, TradeApproval
    }

    void "listPending returns latest pending exporter trades and excludes decided"() {
        given:
        Date now = new Date()
        Date before = new Date(now.time - 10000)
        Map payloadPending = [
                role: 'EXPORTER',
                status: 'PENDING',
                tradeId: 'trade-1',
                externalTradeId: 'ext-1',
                originWhiteLabelId: 'WL-EXP',
                targetWhiteLabelId: 'WL-IMP',
                originOfferId: 'offer-1',
                requestedQuantity: 1,
                unitPrice: 10.0
        ]
        new TelemetryEvent(
                whiteLabelId: 'WL-EXP',
                nodeId: 'WL-EXP',
                eventType: 'TRADER_PURCHASE',
                payload: JsonOutput.toJson(payloadPending),
                eventTimestamp: now
        ).save(validate: false, flush: true)

        new TelemetryEvent(
                whiteLabelId: 'WL-EXP',
                nodeId: 'WL-EXP',
                eventType: 'TRADER_PURCHASE',
                payload: JsonOutput.toJson(payloadPending + [status: 'PENDING']),
                eventTimestamp: before
        ).save(validate: false, flush: true)

        new TelemetryEvent(
                whiteLabelId: 'WL-EXP',
                nodeId: 'WL-EXP',
                eventType: 'TRADER_PURCHASE',
                payload: JsonOutput.toJson(payloadPending + [tradeId: 'trade-2', status: 'PENDING']),
                eventTimestamp: now
        ).save(validate: false, flush: true)

        new TradeApproval(tradeId: 'trade-2', originWhiteLabelId: 'WL-EXP', targetWhiteLabelId: 'WL-IMP', decision: 'APPROVED').save(validate: false, flush: true)

        when:
        Map result = service.listPending([:])

        then:
        result.items.size() == 1
        result.items[0].tradeId == 'trade-1'
    }
}
