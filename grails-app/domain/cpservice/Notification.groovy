package cpservice

import groovy.transform.ToString

@ToString(includeNames = true, includes = ['id', 'type', 'title'])
class Notification {

    String id
    String type
    String title
    String message
    String actionType
    String actionPayload
    String tradeId
    String originWhiteLabelId
    String targetWhiteLabelId

    Date dateCreated
    Date lastUpdated

    static hasMany = [recipients: NotificationRecipient]

    static mapping = {
        table 'cp_notifications'
        id generator: 'uuid2', type: 'string', length: 36
        message type: 'text'
        actionPayload type: 'text'
        originWhiteLabelId index: 'idx_cp_notifications_origin'
        targetWhiteLabelId index: 'idx_cp_notifications_target'
        tradeId index: 'idx_cp_notifications_trade'
    }

    static constraints = {
        type blank: false, maxSize: 64
        title blank: false, maxSize: 255
        message blank: false, maxSize: 2000
        actionType nullable: true, maxSize: 80
        actionPayload nullable: true
        tradeId nullable: true, maxSize: 120
        originWhiteLabelId nullable: true, maxSize: 64
        targetWhiteLabelId nullable: true, maxSize: 64
    }
}
