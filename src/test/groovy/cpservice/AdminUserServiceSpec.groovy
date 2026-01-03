package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class AdminUserServiceSpec extends Specification implements ServiceUnitTest<AdminUserService>, DataTest {

    def setupSpec() {
        mockDomains AdminUser
    }

    void "bootstrap creates default users"() {
        given:
        service.grailsApplication.config.security.adminUsers.bootstrap.masterEmail = 'master@xpory.local'
        service.grailsApplication.config.security.adminUsers.bootstrap.masterPassword = 'changeit'
        service.grailsApplication.config.security.adminUsers.bootstrap.traderEmail = 'trader@xpory.local'
        service.grailsApplication.config.security.adminUsers.bootstrap.traderPassword = 'changeit'

        when:
        service.bootstrapUsersIfNeeded()

        then:
        AdminUser.findByEmail('master@xpory.local')?.role == AdminUser.ROLE_MASTER
        AdminUser.findByEmail('trader@xpory.local')?.role == AdminUser.ROLE_TRADER
    }

    void "authenticate validates password"() {
        given:
        service.createUser('user@xpory.local', 'secret', AdminUser.ROLE_TRADER)

        expect:
        service.authenticate('user@xpory.local', 'secret') != null
        service.authenticate('user@xpory.local', 'wrong') == null
    }
}
