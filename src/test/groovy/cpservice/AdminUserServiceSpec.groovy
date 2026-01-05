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
        service.grailsApplication.config.security.adminUsers.bootstrap.managerEmail = 'manager@xpory.local'
        service.grailsApplication.config.security.adminUsers.bootstrap.managerPassword = 'changeit'

        when:
        service.bootstrapUsersIfNeeded()

        then:
        AdminUser.findByEmail('master@xpory.local')?.role == AdminUser.ROLE_MASTER
        AdminUser.findByEmail('trader@xpory.local')?.role == AdminUser.ROLE_TRADER
        AdminUser.findByEmail('manager@xpory.local')?.role == AdminUser.ROLE_MANAGER
    }

    void "authenticate validates password"() {
        given:
        service.createUser('user@xpory.local', 'secret', AdminUser.ROLE_TRADER)

        expect:
        service.authenticate('user@xpory.local', 'secret') != null
        service.authenticate('user@xpory.local', 'wrong') == null
    }

    void "update profile updates name and phone only"() {
        given:
        AdminUser user = service.createUser('profile@xpory.local', 'secret', AdminUser.ROLE_TRADER)

        when:
        AdminUser updated = service.updateProfile(user.id, [name: 'New Name', phone: '+5511999999999'])

        then:
        updated.name == 'New Name'
        updated.phone == '+5511999999999'
    }

    void "update password validates current password"() {
        given:
        AdminUser user = service.createUser('password@xpory.local', 'secret', AdminUser.ROLE_TRADER)

        when:
        service.updatePassword(user.id, 'wrong', 'new-secret')

        then:
        thrown(IllegalArgumentException)

        when:
        AdminUser updated = service.updatePassword(user.id, 'secret', 'new-secret')

        then:
        updated.passwordHash != null
        service.authenticate('password@xpory.local', 'new-secret') != null
    }

    void "update user allows email change"() {
        given:
        AdminUser user = service.createUser('old@xpory.local', 'secret', AdminUser.ROLE_TRADER)

        when:
        AdminUser updated = service.updateUser(user.id, [email: 'new@xpory.local'])

        then:
        updated.email == 'new@xpory.local'
    }
}
