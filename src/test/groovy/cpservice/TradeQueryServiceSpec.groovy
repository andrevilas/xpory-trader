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
        WhiteLabel exporter = new WhiteLabel(name: 'Alpha', contactEmail: 'alpha@test.com').save(validate: false, flush: true)
        WhiteLabel importer = new WhiteLabel(name: 'Beta', contactEmail: 'beta@test.com').save(validate: false, flush: true)

        new TelemetryEvent(
                whiteLabelId: exporter.id,
                nodeId: exporter.id,
                eventType: 'TRADER_PURCHASE',
                eventTimestamp: new Date(),
                payload: JsonOutput.toJson([
                        tradeId: 'trade-1',
                        originWhiteLabelId: exporter.id,
                        targetWhiteLabelId: importer.id,
                        status: 'CONFIRMED',
                        unitPrice: 10,
                        confirmedQuantity: 2
                ])
        ).save(validate: false, flush: true)

        new TelemetryEvent(
                whiteLabelId: exporter.id,
                nodeId: exporter.id,
                eventType: 'TRADER_PURCHASE',
                eventTimestamp: new Date(),
                payload: JsonOutput.toJson([
                        tradeId: 'trade-2',
                        originWhiteLabelId: exporter.id,
                        targetWhiteLabelId: importer.id,
                        status: 'PENDING',
                        unitPrice: 5,
                        requestedQuantity: 1
                ])
        ).save(validate: false, flush: true)

        new TelemetryEvent(
                whiteLabelId: importer.id,
                nodeId: importer.id,
                eventType: 'TRADER_PURCHASE',
                eventTimestamp: new Date(),
                payload: JsonOutput.toJson([
                        tradeId: 'trade-3',
                        originWhiteLabelId: 'WL-B',
                        targetWhiteLabelId: exporter.id,
                        status: 'REJECTED',
                        unitPrice: 7,
                        requestedQuantity: 1
                ])
        ).save(validate: false, flush: true)

        when:
        Map result = service.list([sourceId: exporter.id, targetId: importer.id, status: 'PENDING', limit: 1, offset: 0])

        then:
        result.count == 1
        result.items.size() == 1
        result.items[0].tradeId == 'trade-2'
        result.items[0].wlExporterName == 'Alpha'
        result.items[0].wlImporterName == 'Beta'
    }
}
