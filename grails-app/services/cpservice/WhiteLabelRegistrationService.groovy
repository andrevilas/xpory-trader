package cpservice

import grails.gorm.transactions.Transactional
import java.util.UUID

/**
 * Handles lifecycle operations related to whitelabel partner onboarding.
 */
@Transactional
class WhiteLabelRegistrationService {

    WhiteLabel register(Map payload) {
        if (!payload?.name) {
            throw new IllegalArgumentException('name is required')
        }
        if (!payload?.contactEmail) {
            throw new IllegalArgumentException('contactEmail is required')
        }

        String id = payload.id ?: UUID.randomUUID().toString()

        def whiteLabel = new WhiteLabel(
                id: id,
                name: payload.name,
                description: payload.description,
                contactEmail: payload.contactEmail,
                gatewayUrl: payload.gatewayUrl,
                status: payload.status ?: 'active'
        )

        Map policyPayload = (payload.policy ?: [:]) as Map
        def policy = new WhiteLabelPolicy(
                whiteLabel: whiteLabel,
                importEnabled: policyPayload.importEnabled ?: false,
                exportEnabled: policyPayload.exportEnabled ?: false,
                exportDelaySeconds: (policyPayload.exportDelaySeconds ?: 0) as Integer,
                visibilityEnabled: policyPayload.visibilityEnabled ?: false,
                policyRevision: policyPayload.policyRevision ?: policyPayload.version ?: 'baseline'
        )

        whiteLabel.baselinePolicy = policy

        whiteLabel.save(flush: true, failOnError: true)

        return whiteLabel
    }
}
