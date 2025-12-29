package cpservice

import grails.gorm.transactions.Transactional
import io.micrometer.core.instrument.MeterRegistry

@Transactional
class TradeMetricsService {

    private final MeterRegistry meterRegistry

    TradeMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry
    }

    void recordTradeStatus(String status) {
        String tag = status ?: 'unknown'
        meterRegistry.counter('cp.trader.purchase.status', 'status', tag).increment()
    }
}

