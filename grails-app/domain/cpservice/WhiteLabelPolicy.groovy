package cpservice

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Baseline policy definition for a whitelabel partner. Acts as the default enforcement
 * configuration until overridden by runtime rules (future waves).
 */
@EqualsAndHashCode(includes = 'id')
@ToString(includes = ['id', 'whiteLabel', 'importEnabled', 'exportEnabled'], includeNames = true)
class WhiteLabelPolicy {

    String id
    WhiteLabel whiteLabel
    boolean importEnabled = false
    boolean exportEnabled = false
    Integer exportDelaySeconds = 0
    Integer exportDelayDays = 0
    boolean visibilityEnabled = false
    String visibilityWlsJson = '[]'
    String policyRevision = 'baseline'
    String updatedBy
    String updatedSource
    Date effectiveFrom = new Date()

    Date dateCreated
    Date lastUpdated

    static belongsTo = [whiteLabel: WhiteLabel]
    static transients = ['visibilityWls']

    static mapping = {
        table 'cp_white_label_policies'
        id generator: 'uuid2', type: 'string', length: 36
        visibilityWlsJson column: 'visibility_wls', type: 'text'
    }

    static constraints = {
        whiteLabel nullable: false
        exportDelaySeconds nullable: false, min: 0, max: 86400
        exportDelayDays nullable: false, min: 0, max: 365
        policyRevision blank: false, maxSize: 50
        updatedBy nullable: true, maxSize: 120
        updatedSource nullable: true, maxSize: 120
        effectiveFrom nullable: false
        visibilityWlsJson nullable: false
    }

    List<String> getVisibilityWls() {
        if (!visibilityWlsJson) {
            return []
        }
        def parsed = new JsonSlurper().parseText(visibilityWlsJson)
        if (parsed instanceof Collection) {
            return (parsed as Collection).collect { it?.toString() }.findAll { it }
        }
        []
    }

    void setVisibilityWls(Collection value) {
        visibilityWlsJson = JsonOutput.toJson((value ?: []).collect { it?.toString() }.findAll { it })
    }
}
