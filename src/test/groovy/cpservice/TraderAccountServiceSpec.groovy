package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class TraderAccountServiceSpec extends Specification implements ServiceUnitTest<TraderAccountService>, DataTest {

    WhiteLabel whiteLabel

    void setupSpec() {
        mockDomains(WhiteLabel, TraderAccount, TelemetryEvent)
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

    void 'upsert creates trader account'() {
        when:
        TraderAccount trader = service.upsert(whiteLabel, [name: 'Trader One', status: 'active', contactEmail: 'trade@example.com'], 'admin-1')

        then:
        trader
        trader.whiteLabel == whiteLabel
        trader.name == 'Trader One'
        trader.status == 'active'
        trader.contactEmail == 'trade@example.com'
        trader.createdByAdmin == 'admin-1'
    }

    void 'processTelemetry records confirmation'() {
        given:
        TraderAccount trader = service.upsert(whiteLabel, [name: 'Trader One'], 'admin-1')
        TelemetryEvent event = new TelemetryEvent(whiteLabelId: whiteLabel.id, eventType: 'trader.account.confirmed')

        when:
        service.processTelemetry(event, [cpTraderId: trader.id, confirmedAt: new Date(1234)])

        then:
        TraderAccount reloaded = TraderAccount.get(trader.id)
        reloaded.confirmedAt
    }

    @Override
    Class[] getDomainClassesToMock() {
        return [WhiteLabel, TraderAccount, TelemetryEvent]
    }
}
