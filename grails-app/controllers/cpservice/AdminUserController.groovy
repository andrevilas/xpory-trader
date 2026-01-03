package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class AdminUserController {

    static responseFormats = ['json']

    AdminUserService adminUserService

    def index() {
        Integer limit = params.int('limit') ?: 100
        Integer offset = params.int('offset') ?: 0
        List<AdminUser> users = adminUserService.listUsers(limit, offset)
        Number total = adminUserService.countUsers()
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
                items: users.collect { AdminUser user ->
                    [
                            id: user.id,
                            email: user.email,
                            role: user.role,
                            status: user.status,
                            lastLoginAt: user.lastLoginAt
                    ]
                },
                count: total
        ] as JSON)
    }

    def save() {
        Map payload = request.JSON as Map ?: [:]
        try {
            AdminUser user = adminUserService.createUser(
                    payload.email?.toString(),
                    payload.password?.toString(),
                    payload.role?.toString()
            )
            render status: HttpStatus.CREATED.value(), contentType: 'application/json', text: ([
                    id: user.id,
                    email: user.email,
                    role: user.role,
                    status: user.status
            ] as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json', text: ([error: ex.message] as JSON)
        }
    }

    def update() {
        String userId = params.id
        Map payload = request.JSON as Map ?: [:]
        try {
            AdminUser user = adminUserService.updateUser(userId, payload)
            render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
                    id: user.id,
                    email: user.email,
                    role: user.role,
                    status: user.status
            ] as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json', text: ([error: ex.message] as JSON)
        }
    }

    def resetPassword() {
        String userId = params.id
        Map payload = request.JSON as Map ?: [:]
        try {
            AdminUser user = adminUserService.resetPassword(userId, payload.password?.toString())
            render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
                    id: user.id,
                    email: user.email,
                    role: user.role,
                    status: user.status
            ] as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json', text: ([error: ex.message] as JSON)
        }
    }
}
