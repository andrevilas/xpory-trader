package cpservice

import groovy.transform.ToString

@ToString(includeNames = true, includes = ['id', 'notification', 'user'])
class NotificationRecipient {

    String id
    Notification notification
    AdminUser user
    Date readAt
    Date deliveredAt

    Date dateCreated
    Date lastUpdated

    static belongsTo = [notification: Notification, user: AdminUser]

    static mapping = {
        table 'cp_notification_recipients'
        id generator: 'uuid2', type: 'string', length: 36
        notification column: 'notification_id'
        user column: 'user_id'
        readAt column: 'read_at'
        deliveredAt column: 'delivered_at'
        notification index: 'idx_cp_notification_recipient_notification'
        user index: 'idx_cp_notification_recipient_user'
    }

    static constraints = {
        notification nullable: false
        user nullable: false
        readAt nullable: true
        deliveredAt nullable: true
    }
}
