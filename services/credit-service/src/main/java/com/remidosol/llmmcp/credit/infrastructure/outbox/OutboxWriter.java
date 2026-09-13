package com.remidosol.llmmcp.credit.infrastructure.outbox;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * The write half of the transactional outbox (ADR-0009): appends the full envelope as a
 * row in the SAME database transaction as the state change that caused it. Column names follow
 * the Debezium outbox EventRouter defaults so the CDC publisher needs no mapping; the poller reads
 * the same row. Plain SQL via JdbcClient: it shares the JPA transaction's connection, and the
 * outbox is not a domain concept that deserves an entity.
 *
 * <p>The current trace context is stored with the row ({@code traceparent}): an outbox breaks the
 * thread-local trace between "state change" and "Kafka send", so the context must travel through
 * the database like the event itself (ADR-0024).
 */
@Component
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final TraceContextCarrier trace;

    public OutboxWriter(JdbcClient jdbc, TraceContextCarrier trace) {
        this.jdbc = jdbc;
        this.trace = trace;
    }

    public void append(EventEnvelope envelope) {
        jdbc.sql("insert into outbox (id, aggregatetype, aggregateid, type, payload, created_at, traceparent) "
                        + "values (:id, :aggregateType, :aggregateId, :type, cast(:payload as jsonb), :createdAt, :traceparent)")
                .param("id", envelope.eventId())
                .param("aggregateType", envelope.aggregateType())
                .param("aggregateId", envelope.aggregateId())
                .param("type", envelope.eventType())
                .param("payload", envelope.toJson())
                .param("createdAt", Timestamp.from(envelope.occurredAt()))
                .param("traceparent", trace.current().orElse(null))
                .update();
    }
}
