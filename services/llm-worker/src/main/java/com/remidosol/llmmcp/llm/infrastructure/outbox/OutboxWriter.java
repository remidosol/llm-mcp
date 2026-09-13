package com.remidosol.llmmcp.llm.infrastructure.outbox;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * The write half of the transactional outbox (PRD §4.8, ADR-0009): appends the full envelope as a
 * row in the SAME database transaction as the state change that caused it. Column names follow
 * the Debezium outbox EventRouter defaults so the CDC publisher needs no mapping; the poller reads
 * the same row. Plain SQL via JdbcClient: it shares the JPA transaction's connection, and the
 * outbox is not a domain concept that deserves an entity.
 */
@Component
public class OutboxWriter {

    private final JdbcClient jdbc;

    public OutboxWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void append(EventEnvelope envelope) {
        jdbc.sql("insert into outbox (id, aggregatetype, aggregateid, type, payload, created_at) "
                        + "values (:id, :aggregateType, :aggregateId, :type, cast(:payload as jsonb), :createdAt)")
                .param("id", envelope.eventId())
                .param("aggregateType", envelope.aggregateType())
                .param("aggregateId", envelope.aggregateId())
                .param("type", envelope.eventType())
                .param("payload", envelope.toJson())
                .param("createdAt", Timestamp.from(envelope.occurredAt()))
                .update();
    }
}
