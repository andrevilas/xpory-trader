package cpservice

import grails.core.GrailsApplication
import groovy.util.logging.Slf4j

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Slf4j
class TradeReconciliationSchedulerService {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor({ Runnable task ->
        Thread thread = new Thread(task, 'cp-trade-reconciliation')
        thread.daemon = true
        return thread
    })

    GrailsApplication grailsApplication
    TradeReconciliationService tradeReconciliationService

    void start() {
        def cfg = grailsApplication?.config?.tradeReconciliation ?: [:]
        boolean enabled = cfg.enabled in [true, 'true', null]
        if (!enabled) {
            log.info('Trade reconciliation scheduler disabled')
            return
        }
        long initialDelayMinutes = (cfg.initialDelayMinutes ?: 2) as long
        long intervalMinutes = (cfg.intervalMinutes ?: 15) as long
        if (intervalMinutes <= 0) {
            log.warn('Trade reconciliation scheduler disabled due to invalid interval={}', intervalMinutes)
            return
        }

        scheduler.scheduleAtFixedRate({
            try {
                Map result = tradeReconciliationService.reconcile(null, null, null)
                if (result?.diverged) {
                    log.warn('Trade reconciliation divergence detected key={} amountDiff={} ratio={} countDiff={}',
                            result.reconciliationKey, result.amountDiffAbs, result.amountDiffRatio, result.countDiffAbs)
                } else {
                    log.debug('Trade reconciliation ok key={} amountDiff={} ratio={} countDiff={}',
                            result?.reconciliationKey, result?.amountDiffAbs, result?.amountDiffRatio, result?.countDiffAbs)
                }
            } catch (Exception ex) {
                log.error('Trade reconciliation failed', ex)
            }
        }, initialDelayMinutes, intervalMinutes, TimeUnit.MINUTES)

        log.info('Scheduled trade reconciliation every {} minute(s)', intervalMinutes)
    }

    void stop() {
        scheduler.shutdownNow()
    }
}
