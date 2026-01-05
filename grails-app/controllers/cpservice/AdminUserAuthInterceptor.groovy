package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class AdminUserAuthInterceptor {

    AdminAuthService adminAuthService

    AdminUserAuthInterceptor() {
        match(controller: 'adminUser')
        match(controller: 'adminProfile')
        match(controller: 'tradeApproval')
        match(controller: 'notification')
    }

    boolean before() {
        String header = request.getHeader('Authorization')
        if (!header || !header.toLowerCase().startsWith('bearer ')) {
            render status: HttpStatus.UNAUTHORIZED.value(), contentType: 'application/json', text: ([error: 'Missing bearer token'] as JSON)
            return false
        }
        String token = header.substring('bearer '.length()).trim()
        Map claims
        try {
            claims = adminAuthService.parseToken(token)
        } catch (Exception ex) {
            render status: HttpStatus.UNAUTHORIZED.value(), contentType: 'application/json', text: ([error: 'Invalid token'] as JSON)
            return false
        }
        if (!claims?.userId || !claims?.role) {
            render status: HttpStatus.UNAUTHORIZED.value(), contentType: 'application/json', text: ([error: 'Invalid token'] as JSON)
            return false
        }
        request.setAttribute('adminUserId', claims.userId)
        request.setAttribute('adminUserRole', claims.role)
        request.setAttribute('adminUserEmail', claims.email)

        if (controllerName == 'adminUser' && !(claims.role in [AdminUser.ROLE_MASTER, AdminUser.ROLE_MANAGER])) {
            render status: HttpStatus.FORBIDDEN.value(), contentType: 'application/json', text: ([error: 'Forbidden'] as JSON)
            return false
        }
        return true
    }

    boolean after() { true }

    void afterView() {
        // no-op
    }
}
