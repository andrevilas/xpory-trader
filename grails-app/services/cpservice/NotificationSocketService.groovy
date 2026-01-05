package cpservice

import grails.converters.JSON
import grails.gorm.transactions.NotTransactional
import org.springframework.messaging.simp.SimpMessageSendingOperations
import org.springframework.stereotype.Service

@Service
class NotificationSocketService {

    private static final String TOPIC_BASE = '/topic'

    SimpMessageSendingOperations brokerMessagingTemplate

    @NotTransactional
    void sendToUser(String userId, Map payload) {
        if (!userId) {
            return
        }
        Map envelope = [kind: 'notification', payload: payload ?: [:]]
        String destination = "${TOPIC_BASE}/${userId}".toString()
        brokerMessagingTemplate?.convertAndSend(destination, (envelope as JSON).toString())
    }
}
