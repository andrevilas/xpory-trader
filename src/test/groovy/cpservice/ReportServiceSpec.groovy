package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import groovy.json.JsonOutput
import spock.lang.Specification

class ReportServiceSpec extends Specification implements ServiceUnitTest<ReportService>, DataTest {

    def setupSpec() {
        mockDomains Relationship, TelemetryEvent
    }

    void "tradeBalanceSummary aggregates settled totals and applies filters"() {
        given:
        Relationship rel = new Relationship(sourceId: 'WL-A', targetId: 'WL-B', limitAmount: 100G).save(validate: false, flush: true)
        new Relationship(sourceId: 'WL-C', targetId: 'WL-D', limitAmount: 50G).save(validate: false, flush: true)

        Date now = new Date()
        Date yesterday = new Date(now.time - 24L * 60L * 60L * 1000L)

        Map payload = [
                originWhiteLabelId: 'WL-A',
                targetWhiteLabelId: 'WL-B',
                status            : 'CONFIRMED',
                unitPrice         : 10.00,
                requestedQuantity : 2,
                confirmedQuantity : 2,
                eventName         : 'TRADE_SETTLED',
                settlementStatus  : 'SETTLED'
        ]
        new TelemetryEvent(
                whiteLabelId: 'WL-B',
                nodeId: 'WL-B',
                eventType: 'TRADER_PURCHASE',
                payload: JsonOutput.toJson(payload),
                eventTimestamp: now
        ).save(validate: false, flush: true)

        new TelemetryEvent(
                whiteLabelId: 'WL-D',
                nodeId: 'WL-D',
                eventType: 'TRADER_PURCHASE',
                payload: JsonOutput.toJson([
                        originWhiteLabelId: 'WL-C',
                        targetWhiteLabelId: 'WL-D',
                        status            : 'PENDING',
                        unitPrice         : 5.00,
                        requestedQuantity : 1
                ]),
                eventTimestamp: yesterday
        ).save(validate: false, flush: true)

        when:
        Map summary = service.tradeBalanceSummary([wlId: 'WL-A', from: yesterday, to: now])

        then:
        summary.filters.wlId == 'WL-A'
        summary.relationships.size() == 2
        Map metrics = summary.relationships.find { it.sourceId == 'WL-A' && it.targetId == 'WL-B' }?.tradeMetrics
        metrics != null
        metrics.counts.CONFIRMED == 1
        metrics.totals.CONFIRMED == 20G
        metrics.settled.count == 1
        metrics.settled.total == 20G
        summary.relationships.find { it.sourceId == 'WL-C' && it.targetId == 'WL-D' }?.tradeMetrics == [:]
    }
}
