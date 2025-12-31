package cpservice

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import groovy.json.JsonSlurper
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import spock.lang.Specification

@Integration
@Rollback
class PeerTokenIntegrationSpec extends Specification {

    PeerTokenService peerTokenService
    JwtService jwtService
    JwtKeyService jwtKeyService

    def 'peer token emits telemetry and includes claims'() {
        given:
        WhiteLabel importer = new WhiteLabel(name: 'Importer WL', contactEmail: 'importer@wl.com')
        importer.baselinePolicy = new WhiteLabelPolicy(whiteLabel: importer)
        importer.save(failOnError: true, flush: true)
        WhiteLabel target = new WhiteLabel(name: 'Exporter WL', contactEmail: 'exporter@wl.com')
        target.baselinePolicy = new WhiteLabelPolicy(whiteLabel: target)
        target.save(failOnError: true, flush: true)
        new Relationship(sourceId: target.id, targetId: importer.id, status: 'active').save(failOnError: true, flush: true)

        when:
        Map result = peerTokenService.issuePeerToken(importer.id, target.id, ['offers:sync'])

        then:
        result.token
        result.expiresInSeconds

        and:
        TelemetryEvent event = TelemetryEvent.findByEventType('PEER_TOKEN_ISSUED')
        event
        event.whiteLabelId == importer.id
        Map payload = new JsonSlurper().parseText(event.payload) as Map
        payload.targetWlId == target.id
        payload.scopes == ['offers:sync']

        and:
        WhiteLabelSigningKey keyEntry = WhiteLabelSigningKey.findByWhiteLabelAndActive(importer, true)
        Claims claims = Jwts.parser().setSigningKey(jwtKeyService.toPublicKey(keyEntry)).parseClaimsJws(result.token).body
        claims.get('sub')?.toString() == target.id.toString()
        claims.get('wlId') == importer.id
        claims.get('targetWlId') == target.id
    }
}
