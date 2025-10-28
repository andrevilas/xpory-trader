package cpservice

import grails.gorm.transactions.Transactional
import io.micrometer.core.instrument.MeterRegistry

@Transactional
class SignalMetricsService {

    private final MeterRegistry meterRegistry

    SignalMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry
    }

    void recordSignal(String action, String sourceId, String targetId) {
        String actionTag = action ?: 'unknown'
        meterRegistry.counter('cp.imbalance.signals.processed', 'action', actionTag).increment()
    }

    void recordAcknowledgement(String action) {
        String actionTag = action ?: 'unknown'
        meterRegistry.counter('cp.imbalance.signals.acknowledged', 'action', actionTag).increment()
    }
}
