package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import groovy.json.JsonOutput
import spock.lang.Specification

class TradeQueryServiceSpec extends Specification implements ServiceUnitTest<TradeQueryService>, DataTest {

    def setupSpec() {
        mockDomains TelemetryEvent, WhiteLabel
    }

    void "list filters by source/target/status and paginates"() {
        given:
        new WhiteLabel(id: 'WL-A', name: 'Alpha').save(validate: false)
        new WhiteLabel(id: 'WL-B', name: 'Beta').save(validate: false)

        new TelemetryEvent(
                whiteLabelId: 'WL-A',
                nodeId: 'WL-A',
                eventType: 'TRADER_PURCHASE',
                eventTimestamp: new Date(),
                payload: JsonOutput.toJson([
                        tradeId: 'trade-1',
                        originWhiteLabelId: 'WL-A',
                        targetWhiteLabelId: 'WL-B',
                        status: 'CONFIRMED',
                        unitPrice: 10,
                        confirmedQuantity: 2
                ])
        ).save(validate: false)

        new TelemetryEvent(
                whiteLabelId: 'WL-A',
                nodeId: 'WL-A',
                eventType: 'TRADER_PURCHASE',
                eventTimestamp: new Date(),
                payload: JsonOutput.toJson([
                        tradeId: 'trade-2',
                        originWhiteLabelId: 'WL-A',
                        targetWhiteLabelId: 'WL-B',
                        status: 'PENDING',
                        unitPrice: 5,
                        requestedQuantity: 1
                ])
        ).save(validate: false)

        new TelemetryEvent(
                whiteLabelId: 'WL-B',
                nodeId: 'WL-B',
                eventType: 'TRADER_PURCHASE',
                eventTimestamp: new Date(),
                payload: JsonOutput.toJson([
                        tradeId: 'trade-3',
                        originWhiteLabelId: 'WL-B',
                        targetWhiteLabelId: 'WL-A',
                        status: 'REJECTED',
                        unitPrice: 7,
                        requestedQuantity: 1
                ])
        ).save(validate: false)

        when:
        Map result = service.list([sourceId: 'WL-A', targetId: 'WL-B', status: 'PENDING', limit: 1, offset: 0])

        then:
        result.count == 1
        result.items.size() == 1
        result.items[0].tradeId == 'trade-2'
        result.items[0].wlExporterName == 'Alpha'
        result.items[0].wlImporterName == 'Beta'
    }
}
