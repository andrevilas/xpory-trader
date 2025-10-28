package cpservice

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Scope
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import org.slf4j.MDC

import java.util.Collections
import java.util.UUID

class RequestContextInterceptor {

    int order = HIGHEST_PRECEDENCE

    TracingService tracingService

    RequestContextInterceptor() {
        matchAll()
    }

    boolean before() {
        String correlationId = request.getHeader('X-Correlation-Id') ?: UUID.randomUUID().toString()
        request.setAttribute('correlationId', correlationId)
        response.setHeader('X-Correlation-Id', correlationId)
        MDC.put('correlationId', correlationId)

        def openTelemetry = tracingService?.openTelemetry
        def propagator = openTelemetry?.propagators?.textMapPropagator
        Context parentContext = propagator ? propagator.extract(Context.current(), request, HttpServletRequestGetter.INSTANCE) : Context.current()

        def spanBuilder = tracingService?.tracer?.spanBuilder("${controllerName}.${actionName}")
                ?.setSpanKind(SpanKind.SERVER)
                ?.setParent(parentContext)
                ?.setAttribute('http.method', request.method)
                ?.setAttribute('http.target', request.forwardURI ?: request.requestURI)
                ?.setAttribute('correlation.id', correlationId)
        Span span = spanBuilder?.startSpan()
        Scope scope = span?.makeCurrent()
        request.setAttribute('activeSpan', span)
        request.setAttribute('activeScope', scope)
        return true
    }

    boolean after() {
        Span span = request.getAttribute('activeSpan') as Span
        if (span && response.status < 400) {
            span.setStatus(StatusCode.OK)
        }
        span?.setAttribute('http.status_code', response.status)
        return true
    }

    void afterView() {
        Scope scope = request.getAttribute('activeScope') as Scope
        Span span = request.getAttribute('activeSpan') as Span
        try {
            def propagator = tracingService?.openTelemetry?.propagators?.textMapPropagator
            if (propagator && span) {
                propagator.inject(Context.current(), response, HttpServletResponseSetter.INSTANCE)
            }
            scope?.close()
            if (span && response.status >= 400) {
                span.setStatus(StatusCode.ERROR)
            }
        } finally {
            span?.end()
            MDC.remove('correlationId')
        }
    }

    private static final class HttpServletRequestGetter implements TextMapGetter<javax.servlet.http.HttpServletRequest> {
        static final HttpServletRequestGetter INSTANCE = new HttpServletRequestGetter()

        @Override
        Iterable<String> keys(javax.servlet.http.HttpServletRequest carrier) {
            if (!carrier) {
                return Collections.emptyList()
            }
            return Collections.list(carrier.headerNames)
        }

        @Override
        String get(javax.servlet.http.HttpServletRequest carrier, String key) {
            return carrier?.getHeader(key)
        }

        @Override
        String toString() {
            return "HttpServletRequestGetter"
        }
    }

    private static final class HttpServletResponseSetter implements TextMapSetter<javax.servlet.http.HttpServletResponse> {
        static final HttpServletResponseSetter INSTANCE = new HttpServletResponseSetter()

        @Override
        void set(javax.servlet.http.HttpServletResponse carrier, String key, String value) {
            if (carrier != null && key != null && value != null) {
                carrier.setHeader(key, value)
            }
        }
    }
}
