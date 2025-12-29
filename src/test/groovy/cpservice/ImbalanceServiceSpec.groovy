package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class ImbalanceServiceSpec extends Specification implements ServiceUnitTest<ImbalanceService>, DataTest {

    void setup() {
        mockDomain(ImbalanceSignal)
    }

    void "rejects invalid action"() {
        when:
        service.record([sourceId: 'WL-1', targetId: 'WL-2', action: 'freeze'])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'action must be block or unblock'
    }

    void "accepts block action"() {
        when:
        ImbalanceSignal signal = service.record([sourceId: 'WL-1', targetId: 'WL-2', action: 'block'])

        then:
        signal.action == 'block'
    }
}
