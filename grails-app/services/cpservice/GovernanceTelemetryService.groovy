package cpservice

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput

@Transactional
class GovernanceTelemetryService {

    TelemetryEvent recordPolicyPackageSent(String whiteLabelId, Map details = [:]) {
        recordEvent(whiteLabelId, 'POLICY_PACKAGE_SENT', details)
    }

    TelemetryEvent recordRelationshipPackageSent(String sourceWhiteLabelId, String targetWhiteLabelId, Map details = [:]) {
        Map payload = details ? new LinkedHashMap(details) : [:]
        payload.targetId = targetWhiteLabelId
        recordEvent(sourceWhiteLabelId, 'RELATIONSHIP_PACKAGE_SENT', payload)
    }

    TelemetryEvent recordPeerTokenIssued(String importerId, Map details = [:]) {
        recordEvent(importerId, 'PEER_TOKEN_ISSUED', details)
    }

    private TelemetryEvent recordEvent(String whiteLabelId, String eventType, Map details) {
        if (!whiteLabelId) {
            return null
        }
        TelemetryEvent event = new TelemetryEvent(
                whiteLabelId  : whiteLabelId,
                nodeId        : 'control-plane',
                eventType     : eventType,
                payload       : JsonOutput.toJson(details ?: [:]),
                eventTimestamp: new Date()
        )
        event.save(flush: true, failOnError: true)
        return event
    }
}
