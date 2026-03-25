package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class RelationshipServiceSpec extends Specification implements ServiceUnitTest<RelationshipService>, DataTest {

    def setupSpec() {
        mockDomain Relationship
    }

    void "upsert persists approval policy fields from mixed camel and snake case payload"() {
        when:
        Relationship relationship = service.upsert('WL-A', 'WL-B', [
                fxRate                         : 1.25,
                imbalance_limit                : 5000,
                approvalMode                   : 'hybrid',
                manual_approval_above_amount   : 250.50,
                manualApprovalAboveQty         : 3,
                manual_approval_on_first_trade : true,
                manualApprovalOnImbalance      : true,
                updated_by                     : 'admin@test.com',
                updatedSource                  : 'admin-ui'
        ])

        then:
        relationship.approvalMode == Relationship.APPROVAL_MODE_HYBRID
        relationship.manualApprovalAboveAmount == 250.50G
        relationship.manualApprovalAboveQty == 3
        relationship.manualApprovalOnFirstTrade
        relationship.manualApprovalOnImbalance
        relationship.updatedBy == 'admin@test.com'
        relationship.updatedSource == 'admin-ui'
    }

    void "upsert rejects invalid approval mode"() {
        when:
        service.upsert('WL-A', 'WL-B', [approvalMode: 'ALWAYS_REVIEW'])

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'Invalid approvalMode'
    }
}
