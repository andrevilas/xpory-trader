package cpservice

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import grails.converters.JSON
import spock.lang.Specification
import spock.lang.Unroll

import java.time.OffsetDateTime

@Integration
@Rollback
class TelemetryQueryIntegrationSpec extends Specification {

    TelemetryQueryService telemetryQueryService
    String wlAId
    String wlBId

    void setup() {
        TelemetryEvent.withSession {
            TelemetryEvent.executeUpdate('delete from TelemetryEvent')
            WhiteLabel wlA = new WhiteLabel(
                    name: "WL A ${UUID.randomUUID()}",
                    contactEmail: 'wl-a@example.com',
                    status: 'active'
            )
            WhiteLabel wlB = new WhiteLabel(
                    name: "WL B ${UUID.randomUUID()}",
                    contactEmail: 'wl-b@example.com',
                    status: 'active'
            )
            wlA.baselinePolicy = new WhiteLabelPolicy(whiteLabel: wlA)
            wlB.baselinePolicy = new WhiteLabelPolicy(whiteLabel: wlB)
            wlA.save(failOnError: true, flush: true)
            wlB.save(failOnError: true, flush: true)
            wlAId = wlA.id
            wlBId = wlB.id
            new TelemetryEvent(
                    whiteLabelId: wlAId,
                    nodeId: 'wl-a',
                    eventType: 'TRADER_PURCHASE',
                    payload: '{"foo":"bar"}',
                    eventTimestamp: Date.from(OffsetDateTime.parse('2025-12-27T10:00:00Z').toInstant())
            ).save(failOnError: true)
            new TelemetryEvent(
                    whiteLabelId: wlAId,
                    nodeId: 'wl-a',
                    eventType: 'HEARTBEAT',
                    payload: '{"status":"ok"}',
                    eventTimestamp: Date.from(OffsetDateTime.parse('2025-12-28T10:00:00Z').toInstant())
            ).save(failOnError: true)
            new TelemetryEvent(
                    whiteLabelId: wlBId,
                    nodeId: 'wl-b',
                    eventType: 'TRADER_PURCHASE',
                    payload: '{"value":123}',
                    eventTimestamp: Date.from(OffsetDateTime.parse('2025-12-29T10:00:00Z').toInstant())
            ).save(failOnError: true)
        }
    }

    void 'filters by eventType'() {
        when:
        Map result = telemetryQueryService.list([eventType: 'TRADER_PURCHASE', limit: 50, offset: 0])

        then:
        result.count == 2
        result.items*.eventType.every { it == 'TRADER_PURCHASE' }
    }

    void 'filters by whiteLabelId'() {
        when:
        Map result = telemetryQueryService.list([whiteLabelId: wlAId, limit: 50, offset: 0])

        then:
        result.count == 2
        result.items*.whiteLabelId.every { it == wlAId }
    }

    void 'filters by from/to'() {
        when:
        Map result = telemetryQueryService.list([
                from: '2025-12-28T00:00:00Z',
                to  : '2025-12-29T00:00:00Z',
                limit: 50,
                offset: 0
        ])

        then:
        result.count == 1
        result.items[0].eventType == 'HEARTBEAT'
    }

    void 'paginates results'() {
        when:
        Map result = telemetryQueryService.list([limit: 1, offset: 1])

        then:
        result.items.size() == 1
        result.limit == 1
        result.offset == 1
        result.count == 3
    }

    @Unroll
    void 'invalid date returns error'() {
        when:
        telemetryQueryService.list([(param): 'invalid-date'])

        then:
        thrown(IllegalArgumentException)

        where:
        param << ['from', 'to']
    }
}
