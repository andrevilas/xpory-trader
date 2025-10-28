package cpservice

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(includes = 'id')
@ToString(includes = ['id', 'sourceId', 'targetId', 'action'], includeNames = true)
class ImbalanceSignal {

    String id
    String sourceId
    String targetId
    String action // block or unblock
    String reason
    String initiatedBy
    Date effectiveFrom = new Date()
    Date effectiveUntil
    Boolean acknowledged = false
    String acknowledgedBy
    Date acknowledgedAt

    Date dateCreated
    Date lastUpdated

    static mapping = {
        table 'cp_imbalance_signals'
        id generator: 'uuid2', type: 'string', length: 36
        sourceId index: 'idx_cp_imbalance_src'
        targetId index: 'idx_cp_imbalance_dst'
    }

    static constraints = {
        sourceId blank: false, maxSize: 36
        targetId blank: false, maxSize: 36
        action inList: ['block', 'unblock']
        reason nullable: true, maxSize: 500
        initiatedBy nullable: true, maxSize: 120
        effectiveFrom nullable: false
        effectiveUntil nullable: true
        acknowledged nullable: false
        acknowledgedBy nullable: true, maxSize: 120
        acknowledgedAt nullable: true
    }
}
