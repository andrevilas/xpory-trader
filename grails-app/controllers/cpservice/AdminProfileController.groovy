package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class AdminProfileController {

    static responseFormats = ['json']

    AdminUserService adminUserService

    def show() {
        String userId = request.getAttribute('adminUserId')?.toString()
        if (!userId) {
            render status: HttpStatus.UNAUTHORIZED.value(), contentType: 'application/json', text: ([error: 'Unauthorized'] as JSON)
            return
        }
        AdminUser user = AdminUser.get(userId)
        if (!user) {
            render status: HttpStatus.NOT_FOUND.value(), contentType: 'application/json', text: ([error: 'user not found'] as JSON)
            return
        }
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
                id: user.id,
                email: user.email,
                name: user.name,
                phone: user.phone,
                role: user.role,
                status: user.status,
                lastLoginAt: user.lastLoginAt
        ] as JSON)
    }

    def update() {
        String userId = request.getAttribute('adminUserId')?.toString()
        if (!userId) {
            render status: HttpStatus.UNAUTHORIZED.value(), contentType: 'application/json', text: ([error: 'Unauthorized'] as JSON)
            return
        }
        Map payload = request.JSON as Map ?: [:]
        try {
            AdminUser user = adminUserService.updateProfile(userId, payload)
            render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
                    id: user.id,
                    email: user.email,
                    name: user.name,
                    phone: user.phone,
                    role: user.role,
                    status: user.status
            ] as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json', text: ([error: ex.message] as JSON)
        }
    }

    def updatePassword() {
        String userId = request.getAttribute('adminUserId')?.toString()
        if (!userId) {
            render status: HttpStatus.UNAUTHORIZED.value(), contentType: 'application/json', text: ([error: 'Unauthorized'] as JSON)
            return
        }
        Map payload = request.JSON as Map ?: [:]
        try {
            AdminUser user = adminUserService.updatePassword(
                    userId,
                    payload.currentPassword?.toString(),
                    payload.newPassword?.toString()
            )
            render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
                    id: user.id,
                    email: user.email,
                    name: user.name,
                    phone: user.phone,
                    role: user.role,
                    status: user.status
            ] as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json', text: ([error: ex.message] as JSON)
        }
    }
}
