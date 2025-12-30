package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

import java.net.HttpURLConnection
import java.io.ByteArrayInputStream

class TraderAccountSyncServiceSpec extends Specification implements ServiceUnitTest<TraderAccountSyncService>, DataTest {

    WhiteLabel whiteLabel

    void setupSpec() {
        mockDomains(WhiteLabel, WhiteLabelPolicy, TraderAccount)
    }

    void setup() {
        WhiteLabelPolicy policy = new WhiteLabelPolicy(
                importEnabled: false,
                exportEnabled: false,
                exportDelaySeconds: 0,
                exportDelayDays: 0,
                visibilityEnabled: false,
                visibilityWls: [],
                policyRevision: 'baseline',
                effectiveFrom: new Date()
        )
        whiteLabel = new WhiteLabel(
                id: 'wl-1',
                name: 'WL1',
                contactEmail: 'ops@example.com',
                status: 'active',
                baselinePolicy: policy
        )
        whiteLabel.save(validate: false, flush: true, failOnError: true)

    }

    void cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(URL)
    }

    void "sync returns 404 when white label is missing"() {
        when:
        Map result = service.syncFromWhiteLabel('missing')

        then:
        result.status == 404
        result.body.ok == false
        result.body.error == 'Unknown white label'
    }

    void "sync returns 400 when gateway url is missing"() {
        when:
        Map result = service.syncFromWhiteLabel(whiteLabel.id)

        then:
        result.status == 400
        result.body.ok == false
        result.body.error == 'Missing gatewayUrl'
    }

    void "sync upserts trader account from WL payload"() {
        given:
        whiteLabel.gatewayUrl = 'https://wl.example.com'
        whiteLabel.save(validate: false, flush: true, failOnError: true)

        String payload = '{"id":"cp-trader-1","name":"Trader One","status":"active","contactEmail":"trader@wl.com"}'
        def connection = Stub(HttpURLConnection) {
            getResponseCode() >> 200
            getInputStream() >> new ByteArrayInputStream(payload.bytes)
            getErrorStream() >> null
            disconnect() >> null
        }
        URL.metaClass.openConnection = { -> connection }

        service.jwtService = Stub(JwtService) {
            issueToken(whiteLabel.id, []) >> 'jwt-token'
        }

        service.traderAccountService = Mock(TraderAccountService) {
            1 * upsert(whiteLabel, { Map data -> data.id == 'cp-trader-1' }, 'admin-1') >>
                    Stub(TraderAccount) {
                        getId() >> 'cp-trader-1'
                    }
        }

        when:
        Map result = service.syncFromWhiteLabel(whiteLabel.id, 'admin-1', 'corr-1')

        then:
        result.status == 200
        result.body.ok == true
        result.body.traderId == 'cp-trader-1'
        result.body.wlId == whiteLabel.id
    }

    @Override
    Class[] getDomainClassesToMock() {
        return [WhiteLabel, WhiteLabelPolicy, TraderAccount]
    }
}
