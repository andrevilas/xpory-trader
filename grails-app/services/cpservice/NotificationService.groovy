package cpservice

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.math.RoundingMode

@Transactional
class NotificationService {

    static final String TYPE_TRADE_NEW = 'TRADE_NEW'
    static final String TYPE_TRADE_PENDING = 'TRADE_PENDING'
    static final String TYPE_LIMIT_WARNING = 'LIMIT_WARNING'
    static final String TYPE_LIMIT_REACHED = 'LIMIT_REACHED'

    static final String ACTION_OPEN_TRADES = 'OPEN_TRADES'
    static final String ACTION_OPEN_PENDING_TRADES = 'OPEN_PENDING_TRADES'
    static final String ACTION_OPEN_RELATIONSHIP = 'OPEN_RELATIONSHIP'

    NotificationSocketService notificationSocketService

    void processTradeTelemetry(TelemetryEvent event, Map payload) {
        if (!payload) {
            return
        }
        String tradeId = resolveTradeId(payload, event)
        String originId = payload.originWhiteLabelId?.toString()
        String targetId = payload.targetWhiteLabelId?.toString()
        if (!tradeId || !originId || !targetId) {
            return
        }
        String status = payload.status?.toString()?.toUpperCase() ?: 'UNKNOWN'
        String role = payload.role?.toString()?.toUpperCase()

        if (status == 'CONFIRMED' && !notificationExistsForTrade(TYPE_TRADE_NEW, tradeId)) {
            createTradeNotification(
                    TYPE_TRADE_NEW,
                    'Novo trade',
                    buildTradeMessage(payload, originId, targetId),
                    ACTION_OPEN_TRADES,
                    [sourceId: originId, targetId: targetId],
                    tradeId,
                    originId,
                    targetId
            )
        }

        if (status == 'PENDING' && role == 'EXPORTER' && !notificationExistsForTrade(TYPE_TRADE_PENDING, tradeId)) {
            createTradeNotification(
                    TYPE_TRADE_PENDING,
                    'Trade pendente',
                    buildTradeMessage(payload, originId, targetId),
                    ACTION_OPEN_PENDING_TRADES,
                    [sourceId: originId, targetId: targetId],
                    tradeId,
                    originId,
                    targetId
            )
        }

        evaluateRelationshipLimit(originId, targetId)
    }

    NotificationRecipient markRead(String userId, String notificationId) {
        NotificationRecipient recipient = NotificationRecipient.findByUserAndNotification(AdminUser.get(userId), Notification.get(notificationId))
        if (!recipient) {
            return null
        }
        if (!recipient.readAt) {
            recipient.readAt = new Date()
            recipient.save(flush: true, failOnError: true)
        }
        return recipient
    }

    Map listForUser(String userId, int limit, int offset) {
        int safeLimit = Math.min(Math.max(limit, 1), 200)
        int safeOffset = Math.max(offset, 0)
        List<NotificationRecipient> recipients = NotificationRecipient.executeQuery(
                'from NotificationRecipient nr where nr.user.id = :userId order by nr.notification.dateCreated desc',
                [userId: userId],
                [max: safeLimit, offset: safeOffset]
        )
        Long total = NotificationRecipient.executeQuery(
                'select count(nr.id) from NotificationRecipient nr where nr.user.id = :userId',
                [userId: userId]
        )[0] as Long

        return [
                items : recipients.collect { buildRecipientPayload(it) },
                count : total,
                limit : safeLimit,
                offset: safeOffset
        ]
    }

    long unreadCount(String userId) {
        return NotificationRecipient.executeQuery(
                'select count(nr.id) from NotificationRecipient nr where nr.user.id = :userId and nr.readAt is null',
                [userId: userId]
        )[0] as Long
    }

    private void createTradeNotification(String type, String title, String message, String actionType, Map actionPayload,
                                         String tradeId, String originId, String targetId) {
        List<AdminUser> recipients = listActiveUsers()
        if (!recipients) {
            return
        }
        Notification notification = new Notification(
                type: type,
                title: title,
                message: message,
                actionType: actionType,
                actionPayload: toJson(actionPayload),
                tradeId: tradeId,
                originWhiteLabelId: originId,
                targetWhiteLabelId: targetId
        )
        notification.save(flush: true, failOnError: true)
        dispatch(notification, recipients)
    }

    private void evaluateRelationshipLimit(String originId, String targetId) {
        Relationship relationship = Relationship.findBySourceIdAndTargetId(originId, targetId)
        if (!relationship) {
            return
        }
        BigDecimal limit = relationship.limitAmount ?: BigDecimal.ZERO
        if (limit <= 0) {
            return
        }
        BigDecimal total = calculateRelationshipTotal(originId, targetId)
        if (total == null) {
            return
        }
        BigDecimal ratio = total / limit
        if (ratio >= 1.0G) {
            if (!notificationExistsForRelationship(TYPE_LIMIT_REACHED, originId, targetId)) {
                createLimitNotification(TYPE_LIMIT_REACHED, 'Limite atingido', originId, targetId, total, limit)
            }
        } else if (ratio >= 0.7G) {
            if (!notificationExistsForRelationship(TYPE_LIMIT_WARNING, originId, targetId)) {
                createLimitNotification(TYPE_LIMIT_WARNING, 'Limite proximo', originId, targetId, total, limit)
            }
        }
    }

    private void createLimitNotification(String type, String title, String originId, String targetId, BigDecimal total, BigDecimal limit) {
        List<AdminUser> recipients = listUsersByRoles([AdminUser.ROLE_MASTER, AdminUser.ROLE_MANAGER])
        if (!recipients) {
            return
        }
        Map names = resolveWhiteLabelNames(originId, targetId)
        String message = "${names.origin ?: originId} -> ${names.target ?: targetId} | ${formatAmount(total)} / ${formatAmount(limit)}"
        Notification notification = new Notification(
                type: type,
                title: title,
                message: message,
                actionType: ACTION_OPEN_RELATIONSHIP,
                actionPayload: toJson([sourceId: originId, targetId: targetId]),
                originWhiteLabelId: originId,
                targetWhiteLabelId: targetId
        )
        notification.save(flush: true, failOnError: true)
        dispatch(notification, recipients)
    }

    private void dispatch(Notification notification, List<AdminUser> recipients) {
        recipients.each { AdminUser user ->
            NotificationRecipient recipient = new NotificationRecipient(
                    notification: notification,
                    user: user,
                    deliveredAt: new Date()
            )
            recipient.save(flush: true, failOnError: true)
            notificationSocketService?.sendToUser(user.id, buildRecipientPayload(recipient))
        }
    }

    private List<AdminUser> listActiveUsers() {
        return AdminUser.findAllByStatus(AdminUser.STATUS_ACTIVE)
    }

    private List<AdminUser> listUsersByRoles(List<String> roles) {
        return AdminUser.findAllByStatusAndRoleInList(AdminUser.STATUS_ACTIVE, roles)
    }

    private boolean notificationExistsForTrade(String type, String tradeId) {
        return Notification.countByTypeAndTradeId(type, tradeId) > 0
    }

    private boolean notificationExistsForRelationship(String type, String originId, String targetId) {
        return Notification.countByTypeAndOriginWhiteLabelIdAndTargetWhiteLabelId(type, originId, targetId) > 0
    }

    private Map buildRecipientPayload(NotificationRecipient recipient) {
        Notification notification = recipient.notification
        return [
                id: notification.id,
                type: notification.type,
                title: notification.title,
                message: notification.message,
                action: buildAction(notification),
                tradeId: notification.tradeId,
                originWhiteLabelId: notification.originWhiteLabelId,
                targetWhiteLabelId: notification.targetWhiteLabelId,
                createdAt: notification.dateCreated,
                readAt: recipient.readAt
        ]
    }

    private Map buildAction(Notification notification) {
        if (!notification.actionType) {
            return null
        }
        return [type: notification.actionType, payload: parseJson(notification.actionPayload)]
    }

    private Map resolveWhiteLabelNames(String originId, String targetId) {
        Map names = [:]
        WhiteLabel origin = originId ? WhiteLabel.get(originId) : null
        WhiteLabel target = targetId ? WhiteLabel.get(targetId) : null
        names.origin = origin?.name
        names.target = target?.name
        return names
    }

    private String buildTradeMessage(Map payload, String originId, String targetId) {
        Map names = resolveWhiteLabelNames(originId, targetId)
        BigDecimal totalValue = resolveTotalValue(payload)
        String totalText = totalValue != null ? formatAmount(totalValue) : 'N/A'
        String originName = names.origin ?: originId
        String targetName = names.target ?: targetId
        return "${originName} -> ${targetName} | ${totalText}".toString()
    }

    private BigDecimal calculateRelationshipTotal(String originId, String targetId) {
        List<TelemetryEvent> events = TelemetryEvent.findAllByEventType('TRADER_PURCHASE')
        BigDecimal total = BigDecimal.ZERO
        events.each { TelemetryEvent event ->
            Map payload = parseJson(event.payload)
            if (!payload) {
                return
            }
            if (payload.originWhiteLabelId?.toString() != originId) {
                return
            }
            if (payload.targetWhiteLabelId?.toString() != targetId) {
                return
            }
            String status = payload.status?.toString()?.toUpperCase() ?: 'UNKNOWN'
            if (!(status in ['CONFIRMED', 'PENDING'])) {
                return
            }
            BigDecimal unitPrice = toBigDecimal(payload.unitPrice)
            BigDecimal requestedQty = toBigDecimal(payload.requestedQuantity)
            BigDecimal confirmedQty = toBigDecimal(payload.confirmedQuantity)
            BigDecimal qty = (status == 'CONFIRMED' && confirmedQty != null) ? confirmedQty : requestedQty
            if (unitPrice != null && qty != null) {
                total += (unitPrice * qty)
            }
        }
        return total
    }

    private BigDecimal resolveTotalValue(Map payload) {
        String status = payload.status?.toString()?.toUpperCase() ?: 'UNKNOWN'
        BigDecimal unitPrice = toBigDecimal(payload.unitPrice)
        BigDecimal requestedQty = toBigDecimal(payload.requestedQuantity)
        BigDecimal confirmedQty = toBigDecimal(payload.confirmedQuantity)
        BigDecimal qty = (status == 'CONFIRMED' && confirmedQty != null) ? confirmedQty : requestedQty
        if (unitPrice == null || qty == null) {
            return null
        }
        return unitPrice * qty
    }

    private String resolveTradeId(Map payload, TelemetryEvent event) {
        return payload.tradeId?.toString() ?: payload.externalTradeId?.toString() ?: event?.id?.toString()
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return null
        }
        if (payload instanceof CharSequence) {
            return payload.toString()
        }
        return JsonOutput.toJson(payload)
    }

    private Map parseJson(String payload) {
        if (!payload) {
            return [:]
        }
        try {
            Object parsed = new JsonSlurper().parseText(payload)
            return parsed instanceof Map ? (Map) parsed : [:]
        } catch (Exception ignored) {
            return [:]
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString())
        }
        try {
            return new BigDecimal(value.toString())
        } catch (Exception ignored) {
            return null
        }
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) {
            return 'N/A'
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }
}
