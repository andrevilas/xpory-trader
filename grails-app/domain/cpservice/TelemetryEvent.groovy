package cpservice

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Append-only telemetry log capturing events emitted by WL nodes.
 */
@EqualsAndHashCode(includes = 'id')
@ToString(includes = ['id', 'whiteLabelId', 'eventType'], includeNames = true)
class TelemetryEvent {

    String id
    String whiteLabelId
    String nodeId
    String eventType
    String idempotencyKey
    String dedupeFingerprint
    String payload
    Date eventTimestamp

    Date dateCreated
    Date lastUpdated

    static mapping = {
        table 'cp_telemetry'
        id generator: 'uuid2', type: 'string', length: 36
        payload type: 'text'
    }

    static constraints = {
        whiteLabelId blank: false, maxSize: 36
        nodeId blank: false, maxSize: 100
        eventType blank: false, maxSize: 120
        idempotencyKey nullable: true, maxSize: 255
        dedupeFingerprint nullable: true, maxSize: 64, unique: true
        payload blank: false
        eventTimestamp nullable: false
    }
}
