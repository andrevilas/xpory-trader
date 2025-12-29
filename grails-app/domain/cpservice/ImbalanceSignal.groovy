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
    String updatedBy
    String updatedSource
    Date effectiveFrom = new Date()
    Date effectiveUntil
    Boolean acknowledged = false
    String acknowledgedBy
    Date acknowledgedAt
    String dispatchStatus = 'pending' // pending, sent, acknowledged, failed
    Integer dispatchAttempts = 0
    Date dispatchedAt
    Date lastDispatchAt
    String dispatchError

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
        updatedBy nullable: true, maxSize: 120
        updatedSource nullable: true, maxSize: 120
        effectiveFrom nullable: false
        effectiveUntil nullable: true
        acknowledged nullable: false
        acknowledgedBy nullable: true, maxSize: 120
        acknowledgedAt nullable: true
        dispatchStatus blank: false, maxSize: 30, inList: ['pending', 'sent', 'acknowledged', 'failed']
        dispatchAttempts nullable: false
        dispatchedAt nullable: true
        lastDispatchAt nullable: true
        dispatchError nullable: true, maxSize: 500
    }
}
