package com.remidosol.llmmcp.credit.infrastructure.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Serializes the current span to a W3C {@code traceparent} string and restores it later in another
 * thread (the outbox poller). Tracing is optional (absent in tests, off in local without OTEL_ENABLED),
 * hence the ObjectProviders: without a Tracer this is a no-op and the outbox works exactly as before.
 */
@Component
public class TraceContextCarrier {

    static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    public TraceContextCarrier(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer.getIfAvailable();
        this.propagator = propagator.getIfAvailable();
    }

    /** The active trace context as a {@code traceparent} header value, if there is one. */
    public Optional<String> current() {
        if (tracer == null || propagator == null) {
            return Optional.empty();
        }
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) {
            return Optional.empty();
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(context, carrier, Map::put);
        return Optional.ofNullable(carrier.get(TRACEPARENT));
    }

    /** Runs {@code work} inside a span continued from {@code traceparent} (or plainly when there is none). */
    public <T> T inContextOf(String traceparent, String spanName, Callable<T> work) throws Exception {
        if (tracer == null || propagator == null || traceparent == null) {
            return work.call();
        }
        Map<String, String> carrier = Map.of(TRACEPARENT, traceparent);
        Span span = propagator.extract(carrier, Map::get).name(spanName).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return work.call();
        } finally {
            span.end();
        }
    }
}
