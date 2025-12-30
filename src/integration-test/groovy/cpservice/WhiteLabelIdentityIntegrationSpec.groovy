package cpservice

import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.hibernate.SessionFactory
import spock.lang.Specification

import java.util.UUID

@Integration
@Rollback
class WhiteLabelIdentityIntegrationSpec extends Specification {

    WhiteLabelRegistrationService whiteLabelRegistrationService
    WhiteLabelIdentityService whiteLabelIdentityService
    SessionFactory sessionFactory

    def "renaming a WL id updates relationships and imbalance signals"() {
        given:
        WhiteLabel whiteLabel = whiteLabelRegistrationService.register([
                name        : 'Old WL',
                contactEmail: 'old@wl.com'
        ])
        WhiteLabel other = whiteLabelRegistrationService.register([
                name        : 'Other WL',
                contactEmail: 'other@wl.com'
        ])
        String originalId = whiteLabel.id
        String otherId = other.id
        String newId = UUID.nameUUIDFromBytes("wl-new-id".bytes).toString()

        new Relationship(
                sourceId: whiteLabel.id,
                targetId: other.id,
                fxRate: 1G,
                limitAmount: 0G,
                status: 'active'
        ).save(flush: true, failOnError: true)
        new Relationship(
                sourceId: other.id,
                targetId: whiteLabel.id,
                fxRate: 1G,
                limitAmount: 0G,
                status: 'active'
        ).save(flush: true, failOnError: true)
        new ImbalanceSignal(
                sourceId: whiteLabel.id,
                targetId: other.id,
                action: 'block',
                initiatedBy: 'tester'
        ).save(flush: true, failOnError: true)
        new ImbalanceSignal(
                sourceId: other.id,
                targetId: whiteLabel.id,
                action: 'unblock',
                initiatedBy: 'tester'
        ).save(flush: true, failOnError: true)

        sessionFactory.currentSession.flush()
        sessionFactory.currentSession.clear()

        when:
        WhiteLabel renamed = whiteLabelIdentityService.renameWhiteLabelId(originalId, newId)

        sessionFactory.currentSession.flush()
        sessionFactory.currentSession.clear()

        then:
        renamed
        renamed.id == newId
        !WhiteLabel.get(originalId)
        WhiteLabel.get(newId)

        Relationship.countBySourceId(originalId) == 0
        Relationship.countByTargetId(originalId) == 0
        Relationship.countBySourceId(newId) == 1
        Relationship.countByTargetId(newId) == 1

        ImbalanceSignal.countBySourceId(originalId) == 0
        ImbalanceSignal.countByTargetId(originalId) == 0
        ImbalanceSignal.countBySourceId(newId) == 1
        ImbalanceSignal.countByTargetId(newId) == 1
    }
}
