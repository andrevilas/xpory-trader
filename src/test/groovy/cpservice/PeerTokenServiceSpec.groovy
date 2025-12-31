package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class PeerTokenServiceSpec extends Specification implements ServiceUnitTest<PeerTokenService>, DataTest {

    JwtService jwtService = Mock()
    GovernanceTelemetryService telemetryService = Mock()

    @Override
    Class[] getDomainClassesToMock() {
        [WhiteLabel, Relationship] as Class[]
    }

    void setup() {
        service.jwtService = jwtService
        service.governanceTelemetryService = telemetryService
    }

    void 'issues peer token when relationship is active'() {
        given:
        WhiteLabel importer = new WhiteLabel(id: 'wl-importer', name: 'Importer', contactEmail: 'i@wl.com')
        importer.save(failOnError: true, validate: false)
        WhiteLabel target = new WhiteLabel(id: 'wl-exporter', name: 'Exporter', contactEmail: 'e@wl.com')
        target.save(failOnError: true, validate: false)
        new Relationship(sourceId: target.id, targetId: importer.id, status: 'active').save(failOnError: true, validate: false)
        jwtService.issuePeerToken(importer.id, target.id, ['offers:sync']) >> 'token-123'
        jwtService.peerTokenTtlSeconds >> 300

        when:
        Map result = service.issuePeerToken(importer.id, target.id, ['offers:sync'])

        then:
        result.token == 'token-123'
        result.expiresInSeconds == 300
        1 * telemetryService.recordPeerTokenIssued(importer.id, [targetWlId: target.id, scopes: ['offers:sync']])
    }

    void 'rejects unsupported scopes'() {
        given:
        WhiteLabel importer = new WhiteLabel(id: 'wl-importer', name: 'Importer', contactEmail: 'i@wl.com')
        importer.save(failOnError: true, validate: false)
        WhiteLabel target = new WhiteLabel(id: 'wl-exporter', name: 'Exporter', contactEmail: 'e@wl.com')
        target.save(failOnError: true, validate: false)

        when:
        service.issuePeerToken(importer.id, target.id, ['unknown:scope'])

        then:
        thrown(IllegalArgumentException)
    }

    void 'rejects inactive relationship'() {
        given:
        WhiteLabel importer = new WhiteLabel(id: 'wl-importer', name: 'Importer', contactEmail: 'i@wl.com')
        importer.save(failOnError: true, validate: false)
        WhiteLabel target = new WhiteLabel(id: 'wl-exporter', name: 'Exporter', contactEmail: 'e@wl.com')
        target.save(failOnError: true, validate: false)

        when:
        service.issuePeerToken(importer.id, target.id, ['offers:sync'])

        then:
        thrown(IllegalStateException)
    }


    void 'rejects inactive white label'() {
        given:
        WhiteLabel importer = new WhiteLabel(id: 'wl-importer', name: 'Importer', contactEmail: 'i@wl.com', status: 'inactive')
        importer.save(failOnError: true, validate: false)
        WhiteLabel target = new WhiteLabel(id: 'wl-exporter', name: 'Exporter', contactEmail: 'e@wl.com')
        target.save(failOnError: true, validate: false)
        new Relationship(sourceId: target.id, targetId: importer.id, status: 'active').save(failOnError: true, validate: false)

        when:
        service.issuePeerToken(importer.id, target.id, ['offers:sync'])

        then:
        thrown(IllegalStateException)
    }



    void 'rejects paused relationship'() {
        given:
        WhiteLabel importer = new WhiteLabel(id: 'wl-importer', name: 'Importer', contactEmail: 'i@wl.com')
        importer.save(failOnError: true, validate: false)
        WhiteLabel target = new WhiteLabel(id: 'wl-exporter', name: 'Exporter', contactEmail: 'e@wl.com')
        target.save(failOnError: true, validate: false)
        new Relationship(sourceId: target.id, targetId: importer.id, status: 'paused').save(failOnError: true, validate: false)

        when:
        service.issuePeerToken(importer.id, target.id, ['offers:sync'])

        then:
        thrown(IllegalStateException)
    }

}