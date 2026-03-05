package cpservice

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import groovy.json.JsonOutput
import spock.lang.Specification

class NotificationServiceSpec extends Specification implements ServiceUnitTest<NotificationService>, DataTest {

    def setupSpec() {
        mockDomains AdminUser, Notification, NotificationRecipient, WhiteLabel, Relationship, TelemetryEvent
    }

    def setup() {
        service.notificationSocketService = Mock(NotificationSocketService)
    }

    void "processTradeTelemetry creates trade notifications for active users"() {
        given:
        AdminUser master = buildUser('master@xpory.local', AdminUser.ROLE_MASTER)
        AdminUser trader = buildUser('trader@xpory.local', AdminUser.ROLE_TRADER)
        new WhiteLabel(id: 'WL-EXP', name: 'WL Exporter', contactEmail: 'exp@test.com').save(validate: false, flush: true)
        new WhiteLabel(id: 'WL-IMP', name: 'WL Importer', contactEmail: 'imp@test.com').save(validate: false, flush: true)
        Map payload = [
                role: 'EXPORTER',
                status: 'PENDING',
                tradeId: 'trade-1',
                originWhiteLabelId: 'WL-EXP',
                targetWhiteLabelId: 'WL-IMP',
                requestedQuantity: 1,
                unitPrice: 100
        ]
        TelemetryEvent event = new TelemetryEvent(
                whiteLabelId: 'WL-EXP',
                nodeId: 'WL-EXP',
                eventType: 'TRADER_PURCHASE',
                payload: JsonOutput.toJson(payload),
                eventTimestamp: new Date()
        ).save(validate: false, flush: true)

        when:
        service.processTradeTelemetry(event, payload)

        then:
        Notification.count() == 1
        NotificationRecipient.count() == 2
        Notification.findByType(NotificationService.TYPE_TRADE_PENDING) != null
        Notification.findByType(NotificationService.TYPE_TRADE_NEW) == null
        2 * service.notificationSocketService.sendToUser(*_)
        master && trader
    }

    void "processTradeTelemetry emits limit notification for master and manager"() {
        given:
        buildUser('master@xpory.local', AdminUser.ROLE_MASTER)
        buildUser('manager@xpory.local', AdminUser.ROLE_MANAGER)
        buildUser('trader@xpory.local', AdminUser.ROLE_TRADER)
        new WhiteLabel(id: 'WL-EXP', name: 'WL Exporter', contactEmail: 'exp@test.com').save(validate: false, flush: true)
        new WhiteLabel(id: 'WL-IMP', name: 'WL Importer', contactEmail: 'imp@test.com').save(validate: false, flush: true)
        new Relationship(sourceId: 'WL-EXP', targetId: 'WL-IMP', limitAmount: 1000, status: 'active').save(validate: false, flush: true)
        Map payload = [
                role: 'EXPORTER',
                status: 'CONFIRMED',
                tradeId: 'trade-2',
                originWhiteLabelId: 'WL-EXP',
                targetWhiteLabelId: 'WL-IMP',
                confirmedQuantity: 1,
                unitPrice: 800
        ]
        TelemetryEvent event = new TelemetryEvent(
                whiteLabelId: 'WL-EXP',
                nodeId: 'WL-EXP',
                eventType: 'TRADER_PURCHASE',
                payload: JsonOutput.toJson(payload),
                eventTimestamp: new Date()
        ).save(validate: false, flush: true)

        when:
        service.processTradeTelemetry(event, payload)

        then:
        Notification.findByType(NotificationService.TYPE_LIMIT_WARNING) != null
        Notification.findByType(NotificationService.TYPE_TRADE_NEW) != null
        NotificationRecipient.findAllByNotification(Notification.findByType(NotificationService.TYPE_LIMIT_WARNING)).size() == 2
    }

    void "createTradeReconciliationAlert sends divergence alert only to master and manager"() {
        given:
        AdminUser master = buildUser('master@xpory.local', AdminUser.ROLE_MASTER)
        AdminUser manager = buildUser('manager@xpory.local', AdminUser.ROLE_MANAGER)
        buildUser('trader@xpory.local', AdminUser.ROLE_TRADER)
        Date from = new Date(1713000000000L)
        Date to = new Date(1713003600000L)

        when:
        service.createTradeReconciliationAlert([
                reconciliationKey: 'recon:1:2',
                from: from,
                to: to,
                amountDiffAbs: 15G,
                amountDiffRatio: 0.025G,
                countDiffAbs: 1
        ])

        then:
        Notification.countByType(NotificationService.TYPE_TRADE_RECONCILIATION_DIVERGENCE) == 1
        Notification alert = Notification.findByType(NotificationService.TYPE_TRADE_RECONCILIATION_DIVERGENCE)
        alert.actionType == NotificationService.ACTION_OPEN_TRADE_BALANCE
        NotificationRecipient.countByNotification(alert) == 2
        NotificationRecipient.findByUserAndNotification(master, alert) != null
        NotificationRecipient.findByUserAndNotification(manager, alert) != null
    }

    void "createTradeReconciliationAlert is idempotent by reconciliation key"() {
        given:
        buildUser('master@xpory.local', AdminUser.ROLE_MASTER)
        Map reconciliation = [
                reconciliationKey: 'recon:dup:1',
                from: new Date(1713000000000L),
                to: new Date(1713003600000L),
                amountDiffAbs: 8G,
                amountDiffRatio: 0.02G,
                countDiffAbs: 0
        ]

        when:
        service.createTradeReconciliationAlert(reconciliation)
        service.createTradeReconciliationAlert(reconciliation)

        then:
        Notification.countByType(NotificationService.TYPE_TRADE_RECONCILIATION_DIVERGENCE) == 1
    }

    private AdminUser buildUser(String email, String role) {
        new AdminUser(
                email: email,
                passwordHash: 'hash',
                role: role,
                status: AdminUser.STATUS_ACTIVE
        ).save(validate: false, flush: true)
    }
}
