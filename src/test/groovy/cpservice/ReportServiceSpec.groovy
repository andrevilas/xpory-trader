package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import groovy.json.JsonOutput
import spock.lang.Specification

class ReportServiceSpec extends Specification implements ServiceUnitTest<ReportService>, DataTest {

    def setup() {
        mockDomains Relationship, TelemetryEvent
    }

    void "tradeBalanceSummary aggregates settled totals and applies filters"() {
        given:
        String wlA = '11111111-1111-1111-1111-111111111111'
        String wlB = '22222222-2222-2222-2222-222222222222'
        String wlC = '33333333-3333-3333-3333-333333333333'
        String wlD = '44444444-4444-4444-4444-444444444444'
        Relationship rel = new Relationship(sourceId: wlA, targetId: wlB, limitAmount: 100G).save(validate: false, flush: true)
        new Relationship(sourceId: wlC, targetId: wlD, limitAmount: 50G).save(validate: false, flush: true)

        Date now = new Date()
        Date yesterday = new Date(now.time - 24L * 60L * 60L * 1000L)

        Map payload = [
                originWhiteLabelId: wlA,
                targetWhiteLabelId: wlB,
                status            : 'CONFIRMED',
                unitPrice         : 10.00,
                requestedQuantity : 2,
                confirmedQuantity : 2,
                eventName         : 'TRADE_SETTLED',
                settlementStatus  : 'SETTLED'
        ]
        new TelemetryEvent(
                whiteLabelId: wlB,
                nodeId: wlB,
                eventType: 'TRADER_PURCHASE',
                payload: JsonOutput.toJson(payload),
                eventTimestamp: now
        ).save(validate: false, flush: true)

        new TelemetryEvent(
                whiteLabelId: wlD,
                nodeId: wlD,
                eventType: 'TRADER_PURCHASE',
                payload: JsonOutput.toJson([
                        originWhiteLabelId: wlC,
                        targetWhiteLabelId: wlD,
                        status            : 'PENDING',
                        unitPrice         : 5.00,
                        requestedQuantity : 1
                ]),
                eventTimestamp: yesterday
        ).save(validate: false, flush: true)

        when:
        Map summary = service.tradeBalanceSummary([wlId: wlA, from: yesterday, to: now])

        then:
        summary.filters.wlId == wlA
        summary.relationships.size() == 1
        Map pair = summary.relationships.find { it.wlAId == wlA && it.wlBId == wlB }
        Map metrics = pair?.tradeMetrics?.aToB
        metrics != null
        metrics.counts.CONFIRMED == 1
        metrics.totals.CONFIRMED == 20G
        metrics.settled.count == 1
        metrics.settled.total == 20G
        pair.totals.totalExported == 20G
        pair.totals.totalImported == 0G
    }

    void "tradeBalanceSummary aggregates bidirectional pair when filtering by exporter/importer"() {
        given:
        String wlA = 'aaaaaaaa-1111-1111-1111-111111111111'
        String wlB = 'bbbbbbbb-2222-2222-2222-222222222222'
        new Relationship(sourceId: wlA, targetId: wlB, limitAmount: 100G).save(validate: false, flush: true)
        new Relationship(sourceId: wlB, targetId: wlA, limitAmount: 80G).save(validate: false, flush: true)

        Date now = new Date()
        new TelemetryEvent(
                whiteLabelId: wlB,
                nodeId: wlB,
                eventType: 'TRADER_PURCHASE',
                payload: JsonOutput.toJson([
                        originWhiteLabelId: wlA,
                        targetWhiteLabelId: wlB,
                        status            : 'CONFIRMED',
                        unitPrice         : 10.00,
                        requestedQuantity : 3,
                        confirmedQuantity : 3
                ]),
                eventTimestamp: now
        ).save(validate: false, flush: true)
        new TelemetryEvent(
                whiteLabelId: wlA,
                nodeId: wlA,
                eventType: 'TRADER_PURCHASE',
                payload: JsonOutput.toJson([
                        originWhiteLabelId: wlB,
                        targetWhiteLabelId: wlA,
                        status            : 'CONFIRMED',
                        unitPrice         : 5.00,
                        requestedQuantity : 4,
                        confirmedQuantity : 4
                ]),
                eventTimestamp: now
        ).save(validate: false, flush: true)

        when:
        Map summary = service.tradeBalanceSummary([wlExporter: wlA, wlImporter: wlB])

        then:
        summary.relationships.size() == 1
        Map pair = summary.relationships[0]
        pair.wlAId == wlA
        pair.wlBId == wlB
        pair.availability.hasAToB
        pair.availability.hasBToA
        pair.totals.totalExported == 30G
        pair.totals.totalImported == 20G
        pair.totals.balance == 10G
    }
}
