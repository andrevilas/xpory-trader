package cpservice

import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import spock.lang.Specification

@Integration
@Rollback
class WhiteLabelFlowIntegrationSpec extends Specification {

    WhiteLabelRegistrationService whiteLabelRegistrationService
    WhiteLabelPolicyService whiteLabelPolicyService
    TelemetryIngestService telemetryIngestService

    def "registering a WL seeds a baseline policy"() {
        when:
        WhiteLabel whiteLabel = whiteLabelRegistrationService.register([
                name        : 'Acme Pay',
                description : 'Demo WL',
                contactEmail: 'ops@acme.com'
        ])

        then:
        whiteLabel
        whiteLabel.id
        whiteLabel.baselinePolicy
        whiteLabel.baselinePolicy.importEnabled == false
        whiteLabel.baselinePolicy.exportDelaySeconds == 0

        when:
        WhiteLabelPolicy policy = whiteLabelPolicyService.fetchBaseline(whiteLabel.id)

        then:
        policy
        policy.whiteLabel.id == whiteLabel.id
    }

    def "telemetry events persist into append only log"() {
        given:
        WhiteLabel whiteLabel = whiteLabelRegistrationService.register([
                name        : 'Logistics WL',
                contactEmail: 'log@wl.com'
        ])

        when:
        List<TelemetryEvent> events = telemetryIngestService.ingest([
                [
                        whiteLabelId : whiteLabel.id,
                        nodeId       : 'node-1',
                        eventType    : 'heartbeat',
                        payload      : [status: 'ok'],
                        eventTimestamp: new Date()
                ]
        ])

        then:
        events.size() == 1
        events.first().whiteLabelId == whiteLabel.id
        TelemetryEvent.count() == 1
    }
}
