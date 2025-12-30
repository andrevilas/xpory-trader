package cpservice

import com.sun.net.httpserver.HttpServer
import grails.core.DefaultGrailsApplication
import grails.testing.gorm.DataTest
import spock.lang.Specification

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class ImbalanceDispatchServiceSpec extends Specification implements DataTest {

    ImbalanceDispatchService service = new ImbalanceDispatchService()
    HttpServer server

    void cleanup() {
        server?.stop(0)
    }

    void 'dispatch posts signal and acknowledges on success'() {
        given:
        AtomicReference<String> authHeader = new AtomicReference<>()
        int port = startServer(authHeader)
        def app = new DefaultGrailsApplication()
        app.config.imbalance = [dispatch: [maxAttempts: 1, backoffMillis: 1, connectTimeoutMillis: 500, readTimeoutMillis: 1000]]
        app.config.security = [mtls: [enabled: false]]
        service.grailsApplication = app
        service.jwtService = Stub(JwtService) {
            issueToken(_, _) >> 'test-token'
        }
        WhiteLabel target = new WhiteLabel(name: 'WL B', contactEmail: 'wl-b@example.com', gatewayUrl: "http://localhost:${port}")
                .save(validate: false, flush: true, failOnError: true)
        ImbalanceSignal signal = new ImbalanceSignal(
                sourceId: 'wl-a',
                targetId: target.id,
                action: 'block',
                effectiveFrom: new Date()
        ).save(validate: false, flush: true, failOnError: true)
        service.imbalanceService = Stub(ImbalanceService) {
            acknowledge(_, _) >> { String id, String acknowledgedBy ->
                signal.acknowledged = true
                signal.acknowledgedBy = acknowledgedBy
                signal.dispatchStatus = 'acknowledged'
                signal
            }
        }

        when:
        ImbalanceSignal result = service.dispatch(signal)

        then:
        result.acknowledged
        result.dispatchStatus == 'acknowledged'
        authHeader.get() == 'Bearer test-token'
    }

    void 'dispatch succeeds over HTTP when mTLS is disabled'() {
        given:
        AtomicReference<String> authHeader = new AtomicReference<>()
        int port = startServer(authHeader)
        def app = new DefaultGrailsApplication()
        app.config.imbalance = [dispatch: [maxAttempts: 1, backoffMillis: 1, connectTimeoutMillis: 500, readTimeoutMillis: 1000]]
        app.config.security = [mtls: [enabled: false]]
        service.grailsApplication = app
        service.jwtService = Stub(JwtService) {
            issueToken(_, _) >> 'test-token'
        }
        WhiteLabel target = new WhiteLabel(name: 'WL B', contactEmail: 'wl-b@example.com', gatewayUrl: "http://localhost:${port}")
                .save(validate: false, flush: true, failOnError: true)
        ImbalanceSignal signal = new ImbalanceSignal(
                sourceId: 'wl-a',
                targetId: target.id,
                action: 'block',
                effectiveFrom: new Date()
        ).save(validate: false, flush: true, failOnError: true)
        service.imbalanceService = Stub(ImbalanceService) {
            acknowledge(_, _) >> { String id, String acknowledgedBy ->
                signal.acknowledged = true
                signal.acknowledgedBy = acknowledgedBy
                signal.dispatchStatus = 'acknowledged'
                signal
            }
        }

        when:
        ImbalanceSignal result = service.dispatch(signal)

        then:
        result.acknowledged
        result.dispatchStatus == 'acknowledged'
        authHeader.get() == 'Bearer test-token'
    }

    void 'dispatch fails when gatewayUrl is missing'() {
        given:
        def app = new DefaultGrailsApplication()
        app.config.imbalance = [dispatch: [maxAttempts: 1, backoffMillis: 1]]
        app.config.security = [mtls: [enabled: false]]
        service.grailsApplication = app
        service.jwtService = Stub(JwtService) {
            issueToken(_, _) >> 'test-token'
        }
        WhiteLabel target = new WhiteLabel(name: 'WL B', contactEmail: 'wl-b@example.com', gatewayUrl: null)
                .save(validate: false, flush: true, failOnError: true)
        ImbalanceSignal signal = new ImbalanceSignal(
                sourceId: 'wl-a',
                targetId: target.id,
                action: 'block',
                effectiveFrom: new Date()
        ).save(validate: false, flush: true, failOnError: true)
        service.imbalanceService = Stub(ImbalanceService)

        when:
        ImbalanceSignal result = service.dispatch(signal)

        then:
        result.dispatchStatus == 'failed'
        result.dispatchError?.contains('Missing gatewayUrl')
    }

    private int startServer(AtomicReference<String> authHeader) {
        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext('/api/v2/control-plane/imbalance/signals') { exchange ->
            authHeader.set(exchange.requestHeaders.getFirst('Authorization'))
            exchange.sendResponseHeaders(202, 0)
            exchange.responseBody.close()
        }
        server.executor = Executors.newSingleThreadExecutor()
        server.start()
        return server.address.port
    }

    @Override
    Class[] getDomainClassesToMock() {
        return [ImbalanceSignal, WhiteLabel]
    }

}
