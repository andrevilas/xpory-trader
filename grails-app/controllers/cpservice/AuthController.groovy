package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class AuthController {

    static responseFormats = ['json']

    AdminUserService adminUserService
    AdminAuthService adminAuthService

    def login() {
        Map payload = request.JSON as Map ?: [:]
        String email = payload.email?.toString()
        String password = payload.password?.toString()
        AdminUser user = adminUserService.authenticate(email, password)
        if (!user) {
            render status: HttpStatus.UNAUTHORIZED.value(), contentType: 'application/json', text: ([error: 'Invalid credentials'] as JSON)
            return
        }
        String token = adminAuthService.issueToken(user)
        int ttlSeconds = (adminAuthService.grailsApplication?.config?.security?.adminUsers?.jwtTtlSeconds ?: 3600) as int
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
                token: token,
                expiresInSeconds: ttlSeconds,
                user: [
                        id: user.id,
                        email: user.email,
                        role: user.role
                ]
        ] as JSON)
    }
}
