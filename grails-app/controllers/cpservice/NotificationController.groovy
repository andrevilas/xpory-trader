package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class NotificationController {

    static responseFormats = ['json']

    NotificationService notificationService

    def index() {
        String userId = request.getAttribute('adminUserId')?.toString()
        Integer limit = params.int('limit') ?: 50
        Integer offset = params.int('offset') ?: 0
        Map result = notificationService.listForUser(userId, limit, offset)
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: (result as JSON)
    }

    def unreadCount() {
        String userId = request.getAttribute('adminUserId')?.toString()
        long count = notificationService.unreadCount(userId)
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([count: count] as JSON)
    }

    def read() {
        String userId = request.getAttribute('adminUserId')?.toString()
        String notificationId = params.id?.toString()
        if (!notificationId) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json', text: ([error: 'notification id is required'] as JSON)
            return
        }
        NotificationRecipient recipient = notificationService.markRead(userId, notificationId)
        if (!recipient) {
            render status: HttpStatus.NOT_FOUND.value(), contentType: 'application/json', text: ([error: 'notification not found'] as JSON)
            return
        }
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
                id: recipient.notification.id,
                readAt: recipient.readAt
        ] as JSON)
    }
}
