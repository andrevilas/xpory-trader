package cpservice

import grails.gorm.transactions.Transactional
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator

/**
 * Minimal tracing facade around OpenTelemetry, enabling span creation for
 * key control-plane flows.
 */
@Transactional
class TracingService {

    private final Tracer tracer
    private final OpenTelemetrySdk openTelemetry

    TracingService() {
        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal()
        this.tracer = openTelemetry.getTracer('xpory-control-plane', '0.1.0')
    }

    def <T> T inSpan(String spanName, Closure<T> work) {
        Span span = tracer.spanBuilder(spanName).startSpan()
        Scope scope = span.makeCurrent()
        try {
            return work.call()
        } catch (Throwable throwable) {
            span.recordException(throwable)
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR)
            throw throwable
        } finally {
            scope?.close()
            span.end()
        }
    }

    Tracer getTracer() {
        return tracer
    }

    OpenTelemetrySdk getOpenTelemetry() {
        return openTelemetry
    }
}
