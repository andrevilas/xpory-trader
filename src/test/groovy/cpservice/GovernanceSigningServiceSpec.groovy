package cpservice

import grails.core.DefaultGrailsApplication
import spock.lang.Specification

class GovernanceSigningServiceSpec extends Specification {

    GovernanceSigningService service = new GovernanceSigningService()

    void setup() {
        def app = new DefaultGrailsApplication()
        app.config.governance = [signatureSecret: 'test-secret']
        service.grailsApplication = app
    }

    void 'signPayload returns deterministic signature'() {
        when:
        Map signatureA = service.signPayload([foo: 'bar', count: 1])
        Map signatureB = service.signPayload([count: 1, foo: 'bar'])

        then:
        signatureA.value == signatureB.value
        signatureA.algorithm == 'HMAC-SHA256'
        signatureA.issuedAt instanceof Date
    }
}
