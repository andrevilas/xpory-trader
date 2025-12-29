package cpservice

import grails.gorm.transactions.Transactional

@Transactional
class ImbalanceService {

    SignalMetricsService signalMetricsService

    ImbalanceSignal record(Map payload) {
        String sourceId = payload.sourceId ?: payload.src
        String targetId = payload.targetId ?: payload.dst
        if (!sourceId || !targetId) {
            throw new IllegalArgumentException('sourceId and targetId are required')
        }
        if (!payload.action) {
            throw new IllegalArgumentException('action is required')
        }
        String action = payload.action.toString().toLowerCase()
        if (!(action in ['block', 'unblock'])) {
            throw new IllegalArgumentException('action must be block or unblock')
        }
        String updatedBy = payload.updatedBy ?: payload.updated_by ?: payload.initiatedBy
        String updatedSource = payload.updatedSource ?: payload.updated_source ?: payload.source
        ImbalanceSignal signal = new ImbalanceSignal(
                sourceId      : sourceId,
                targetId      : targetId,
                action        : action,
                reason        : payload.reason?.toString(),
                initiatedBy   : payload.initiatedBy?.toString(),
                updatedBy     : updatedBy?.toString(),
                updatedSource : updatedSource?.toString(),
                dispatchStatus: 'pending'
        )
        if (payload.effectiveFrom) {
            signal.effectiveFrom = coerceDate(payload.effectiveFrom)
        }
        if (payload.effectiveUntil) {
            signal.effectiveUntil = coerceDate(payload.effectiveUntil)
        }
        signal.save(failOnError: true, flush: true)
        signalMetricsService?.recordSignal(signal.action, signal.sourceId, signal.targetId)
        signal
    }

    ImbalanceSignal acknowledge(String signalId, String acknowledgedBy) {
        if (!signalId) {
            throw new IllegalArgumentException('signalId is required')
        }
        ImbalanceSignal signal = ImbalanceSignal.get(signalId)
        if (!signal) {
            throw new IllegalArgumentException('Signal not found')
        }
        if (!signal.acknowledged) {
            signal.acknowledged = true
            signal.acknowledgedBy = acknowledgedBy ?: 'unknown'
            signal.acknowledgedAt = new Date()
            signal.dispatchStatus = 'acknowledged'
            signal.updatedBy = acknowledgedBy ?: signal.updatedBy
            signal.updatedSource = signal.updatedSource ?: 'ack'
            signal.save(flush: true, failOnError: true)
            signalMetricsService?.recordAcknowledgement(signal.action)
        }
        signal
    }

    protected Date coerceDate(Object value) {
        if (value instanceof Date) {
            return value
        }
        if (value instanceof CharSequence) {
            try {
                return Date.from(java.time.OffsetDateTime.parse(value.toString()).toInstant())
            } catch (Exception ignored) {
                throw new IllegalArgumentException('Invalid date format')
            }
        }
        throw new IllegalArgumentException('Invalid date format')
    }
}
