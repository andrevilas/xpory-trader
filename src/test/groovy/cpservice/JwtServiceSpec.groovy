package cpservice

import grails.core.DefaultGrailsApplication
import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import spock.lang.Specification

class JwtServiceSpec extends Specification implements ServiceUnitTest<JwtService>, DataTest {

    JwtKeyService jwtKeyService = new JwtKeyService()

    @Override
    Class[] getDomainClassesToMock() {
        [WhiteLabel, WhiteLabelPolicy, WhiteLabelSigningKey] as Class[]
    }

    void setup() {
        service.jwtKeyService = jwtKeyService
        service.grailsApplication = new DefaultGrailsApplication()
        service.grailsApplication.config.security = [
                jwt: [
                        issuer            : 'xpory-test',
                        ttlSeconds        : 300,
                        peerTokenTtlSeconds: 120
                ]
        ]
        service.init()
    }

    void 'issuePeerToken sets claims and ttl'() {
        given:
        WhiteLabel importer = new WhiteLabel(id: 'wl-importer', name: 'Importer', contactEmail: 'i@wl.com')
        importer.baselinePolicy = new WhiteLabelPolicy(whiteLabel: importer)
        importer.save(failOnError: true, validate: false)
        WhiteLabel target = new WhiteLabel(id: 'wl-exporter', name: 'Exporter', contactEmail: 'e@wl.com')
        target.baselinePolicy = new WhiteLabelPolicy(whiteLabel: target)
        target.save(failOnError: true, validate: false)
        jwtKeyService.ensureActiveKey(importer.id)

        when:
        String token = service.issuePeerToken(importer.id, target.id, ['offers:sync', 'trader:purchase'])
        WhiteLabelSigningKey keyEntry = WhiteLabelSigningKey.findByWhiteLabelAndActive(importer, true)
        Claims claims = Jwts.parser().setSigningKey(jwtKeyService.toPublicKey(keyEntry)).parseClaimsJws(token).body

        then:
        claims.get('sub')?.toString() == target.id.toString()
        claims.get('aud')?.toString() == target.id.toString()
        claims.get('wlId') == importer.id
        claims.get('targetWlId') == target.id
        (claims.get('scopes') as Collection).containsAll(['offers:sync', 'trader:purchase'])

        and:
        Object expValue = claims.get('exp')
        Object iatValue = claims.get('iat')
        Date exp = expValue instanceof Date ? (Date) expValue : new Date(((expValue as Number) * 1000L) as long)
        Date iat = iatValue instanceof Date ? (Date) iatValue : new Date(((iatValue as Number) * 1000L) as long)
        long ttlSeconds = Math.round((exp.time - iat.time) / 1000.0)
        ttlSeconds > 0
        ttlSeconds <= service.peerTokenTtlSeconds
    }
}
