package cpservice

import grails.testing.web.controllers.ControllerUnitTest
import groovy.json.JsonSlurper
import spock.lang.Specification

class WhiteLabelControllerSpec extends Specification implements ControllerUnitTest<WhiteLabelController> {

    PeerTokenService peerTokenService = Mock()

    void setup() {
        controller.peerTokenService = peerTokenService
    }

    void 'peer token returns 201 with payload'() {
        given:
        request.method = 'POST'
        request.contentType = 'application/json'
        params.id = 'wl-importer'
        request.json = [targetWlId: 'wl-exporter', scopes: ['offers:sync']]
        peerTokenService.issuePeerToken('wl-importer', 'wl-exporter', ['offers:sync']) >>
                [token: 'token-123', expiresInSeconds: 300]

        when:
        controller.peerToken()

        then:
        response.status == 201
        Map body = new JsonSlurper().parseText(response.text) as Map
        body.token == 'token-123'
        body.expiresInSeconds == 300
    }

    void 'peer token returns 400 on invalid input'() {
        given:
        request.method = 'POST'
        request.contentType = 'application/json'
        params.id = 'wl-importer'
        request.json = [targetWlId: null, scopes: []]
        peerTokenService.issuePeerToken('wl-importer', null, []) >> { throw new IllegalArgumentException('bad request') }

        when:
        controller.peerToken()

        then:
        response.status == 400
        Map body = new JsonSlurper().parseText(response.text) as Map
        body.error == 'bad request'
    }

    void 'peer token returns 403 when forbidden'() {
        given:
        request.method = 'POST'
        request.contentType = 'application/json'
        params.id = 'wl-importer'
        request.json = [targetWlId: 'wl-exporter', scopes: ['offers:sync']]
        peerTokenService.issuePeerToken('wl-importer', 'wl-exporter', ['offers:sync']) >>
                { throw new IllegalStateException('relationship not active') }

        when:
        controller.peerToken()

        then:
        response.status == 403
        Map body = new JsonSlurper().parseText(response.text) as Map
        body.error == 'relationship not active'
    }
}
