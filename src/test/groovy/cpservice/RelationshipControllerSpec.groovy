package cpservice

import grails.testing.web.controllers.ControllerUnitTest
import groovy.json.JsonSlurper
import spock.lang.Specification

class RelationshipControllerSpec extends Specification implements ControllerUnitTest<RelationshipController> {

    RelationshipService relationshipService = Mock()
    GovernanceSigningService governanceSigningService = Mock()
    GovernanceTelemetryService governanceTelemetryService = Mock()

    void setup() {
        controller.relationshipService = relationshipService
        controller.governanceSigningService = governanceSigningService
        controller.governanceTelemetryService = governanceTelemetryService
    }

    void "show returns approval policy fields in camel and snake case"() {
        given:
        params.src = 'WL-A'
        params.dst = 'WL-B'
        Relationship relationship = new Relationship(
                sourceId: 'WL-A',
                targetId: 'WL-B',
                fxRate: 1.1G,
                limitAmount: 5000G,
                status: 'active',
                approvalMode: Relationship.APPROVAL_MODE_HYBRID,
                manualApprovalAboveAmount: 300G,
                manualApprovalAboveQty: 2,
                manualApprovalOnFirstTrade: true,
                manualApprovalOnImbalance: false
        )
        relationshipService.fetch('WL-A', 'WL-B') >> relationship
        governanceSigningService.signPayload(_ as Map) >> [algorithm: 'HMAC-SHA256', value: 'sig-123']

        when:
        controller.show()

        then:
        response.status == 200
        Map body = new JsonSlurper().parseText(response.text) as Map
        body.approvalMode == 'HYBRID'
        body.approval_mode == 'HYBRID'
        body.manualApprovalAboveAmount == 300
        body.manual_approval_above_amount == 300
        body.manualApprovalAboveQty == 2
        body.manual_approval_above_qty == 2
        body.manualApprovalOnFirstTrade == true
        body.manual_approval_on_first_trade == true
        body.manualApprovalOnImbalance == false
        body.manual_approval_on_imbalance == false
        body.signature.value == 'sig-123'
    }
}
