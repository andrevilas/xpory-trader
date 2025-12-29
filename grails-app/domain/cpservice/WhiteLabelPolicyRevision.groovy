package cpservice

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(includes = 'id')
@ToString(includes = ['id', 'whiteLabel', 'policyRevision'], includeNames = true)
class WhiteLabelPolicyRevision {

    String id
    WhiteLabel whiteLabel
    WhiteLabelPolicy policy
    String policyRevision
    String payload
    String updatedBy
    String updatedSource
    Date effectiveFrom

    Date dateCreated

    static mapping = {
        table 'cp_white_label_policy_revisions'
        id generator: 'uuid2', type: 'string', length: 36
        payload type: 'text'
    }

    static constraints = {
        whiteLabel nullable: false
        policy nullable: false
        policyRevision blank: false, maxSize: 50
        payload blank: false
        updatedBy nullable: true, maxSize: 120
        updatedSource nullable: true, maxSize: 120
        effectiveFrom nullable: true
    }
}

