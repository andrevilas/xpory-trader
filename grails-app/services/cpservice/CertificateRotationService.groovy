package cpservice

import grails.core.GrailsApplication
import groovy.util.logging.Slf4j
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Coordinates rotation of TLS assets and JWT signing keys.
 */
@Slf4j
class CertificateRotationService {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor({ Runnable task ->
        Thread thread = new Thread(task, 'cp-cert-rotation')
        thread.daemon = true
        return thread
    })

    GrailsApplication grailsApplication
    JwtService jwtService

    void start() {
        long rotationDays = (grailsApplication.config.security.mtls.rotationDays ?: 90) as long
        long initialDelayMinutes = (grailsApplication.config.security.mtls.initialCheckMinutes ?: 5) as long

        scheduler.scheduleAtFixedRate({
            try {
                rotateArtifacts()
            } catch (Exception ex) {
                log.error('Failed rotating certificates/keys', ex)
            }
        }, initialDelayMinutes, TimeUnit.DAYS.toMinutes(rotationDays), TimeUnit.MINUTES)
        log.info('Scheduled certificate and key rotation every {} day(s)', rotationDays)
    }

    void rotateArtifacts() {
        jwtService.refreshSigningMaterial()
        log.info('JWT signing material refreshed from configured source')
        // TLS assets are expected to be rotated by platform automation; this hook allows
        // follow-up verification / health-check logic to be added in later waves.
    }

    void stop() {
        scheduler.shutdownNow()
    }
}
