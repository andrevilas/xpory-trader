package cpservice

import grails.core.GrailsApplication
import groovy.util.logging.Slf4j

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Slf4j
class ExportMetadataSyncSchedulerService {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor({ Runnable task ->
        Thread thread = new Thread(task, 'cp-export-metadata-sync')
        thread.daemon = true
        return thread
    })

    GrailsApplication grailsApplication
    ExportMetadataSyncService exportMetadataSyncService

    void start() {
        def cfg = grailsApplication?.config?.controlPlane?.exportMetadataSync ?: [:]
        boolean enabled = cfg.enabled in [true, 'true', null]
        if (!enabled) {
            log.info('Export metadata sync scheduler disabled')
            return
        }
        long initialDelayMinutes = (cfg.initialDelayMinutes ?: 5) as long
        long intervalMinutes = (cfg.intervalMinutes ?: 60) as long
        if (intervalMinutes <= 0) {
            log.warn('Export metadata sync scheduler disabled due to invalid interval={}', intervalMinutes)
            return
        }
        scheduler.scheduleAtFixedRate({
            try {
                exportMetadataSyncService.syncAll()
            } catch (Exception ex) {
                log.error('Export metadata sync failed', ex)
            }
        }, initialDelayMinutes, intervalMinutes, TimeUnit.MINUTES)
        log.info('Scheduled export metadata sync every {} minute(s)', intervalMinutes)
    }

    void stop() {
        scheduler.shutdownNow()
    }
}
