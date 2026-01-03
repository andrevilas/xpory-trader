package cpservice

import grails.testing.gorm.DomainUnitTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class AdminAuthServiceSpec extends Specification implements ServiceUnitTest<AdminAuthService>, DomainUnitTest<AdminUser> {

    AdminAuthService targetService

    void setup() {
        mockDomain(AdminUser)
        service.grailsApplication = grailsApplication
        targetService = service
        if (service.metaClass.hasProperty(service, 'targetSource')) {
            targetService = service.targetSource.target
        }
        targetService.metaClass.resolveSecret = { -> 'test-secret' }
        targetService.metaClass.resolveTtlSeconds = { -> 120 }
    }

    void "issues and parses admin jwt"() {
        given:
        AdminUser user = new AdminUser(email: 'user@xpory.com', passwordHash: 'hash', role: AdminUser.ROLE_MASTER)
        user.save(validate: false, flush: true)

        when:
        String token = targetService.issueToken(user)
        Map claims = targetService.parseToken(token)

        then:
        claims.userId == user.id.toString()
        claims.email == 'user@xpory.com'
        claims.role == AdminUser.ROLE_MASTER
        claims.expiresAt != null
    }
}
