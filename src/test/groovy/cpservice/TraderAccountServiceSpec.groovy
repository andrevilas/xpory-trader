package cpservice

import grails.test.mixin.TestFor
import grails.test.mixin.domain.DomainClassUnitTestMixin
import spock.lang.Specification

@TestFor(TraderAccountService)
@Mixin(DomainClassUnitTestMixin)
class TraderAccountServiceSpec extends Specification {

    WhiteLabel whiteLabel

    void setup() {
        mockDomain(WhiteLabel)
        mockDomain(TraderAccount)
        whiteLabel = new WhiteLabel(id: 'wl-1', name: 'WL1', contactEmail: 'ops@example.com', status: 'active')
        whiteLabel.save(validate: false)
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
}
