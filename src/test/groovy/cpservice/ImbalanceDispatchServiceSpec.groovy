package cpservice

import com.sun.net.httpserver.HttpServer
import grails.core.DefaultGrailsApplication
import spock.lang.Specification

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class ImbalanceDispatchServiceSpec extends Specification {

    ImbalanceDispatchService service = new ImbalanceDispatchService()
    HttpServer server

    void cleanup() {
        server?.stop(0)
        GroovySystem.metaClassRegistry.removeMetaClass(WhiteLabel)
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
        ImbalanceSignal signal = new ImbalanceSignal(
                id: 'sig-1',
                sourceId: 'wl-a',
                targetId: 'wl-b',
                action: 'block',
                effectiveFrom: new Date()
        )
        signal.metaClass.save = { Map args -> signal }
        service.imbalanceService = Stub(ImbalanceService) {
            acknowledge('sig-1', _) >> { String id, String acknowledgedBy ->
                signal.acknowledged = true
                signal.acknowledgedBy = acknowledgedBy
                signal.dispatchStatus = 'acknowledged'
                signal
            }
        }
        WhiteLabel.metaClass.'static'.get = { String id ->
            new WhiteLabel(id: id, gatewayUrl: "http://localhost:${port}")
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
        ImbalanceSignal signal = new ImbalanceSignal(
                id: 'sig-3',
                sourceId: 'wl-a',
                targetId: 'wl-b',
                action: 'block',
                effectiveFrom: new Date()
        )
        signal.metaClass.save = { Map args -> signal }
        service.imbalanceService = Stub(ImbalanceService) {
            acknowledge('sig-3', _) >> { String id, String acknowledgedBy ->
                signal.acknowledged = true
                signal.acknowledgedBy = acknowledgedBy
                signal.dispatchStatus = 'acknowledged'
                signal
            }
        }
        WhiteLabel.metaClass.'static'.get = { String id ->
            new WhiteLabel(id: id, gatewayUrl: "http://localhost:${port}")
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
        ImbalanceSignal signal = new ImbalanceSignal(
                id: 'sig-2',
                sourceId: 'wl-a',
                targetId: 'wl-b',
                action: 'block',
                effectiveFrom: new Date()
        )
        signal.metaClass.save = { Map args -> signal }
        service.imbalanceService = Stub(ImbalanceService)
        WhiteLabel.metaClass.'static'.get = { String id ->
            new WhiteLabel(id: id, gatewayUrl: null)
        }

        when:
        ImbalanceSignal result = service.dispatch(signal)

        then:
        result.dispatchStatus == 'failed'
        result.dispatchError?.contains('Missing gatewayUrl')
    }

    private int startServer(AtomicReference<String> authHeader) {
        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext('/control-plane/imbalance/signals') { exchange ->
            authHeader.set(exchange.requestHeaders.getFirst('Authorization'))
            exchange.sendResponseHeaders(202, 0)
            exchange.responseBody.close()
        }
        server.executor = Executors.newSingleThreadExecutor()
        server.start()
        return server.address.port
    }
}
