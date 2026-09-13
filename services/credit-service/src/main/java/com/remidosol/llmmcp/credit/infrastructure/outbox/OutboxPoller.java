package com.remidosol.llmmcp.credit.infrastructure.outbox;

import com.remidosol.llmmcp.contracts.EventHeaders;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The read half of the outbox — the polling publisher (ADR-0009, task 3.2). Every tick it claims a
 * batch with {@code FOR UPDATE SKIP LOCKED} (so several replicas never fight over rows), sends each
 * row synchronously ({@code send().get()}: no publish is ever assumed), marks it published, and
 * commits. If Kafka is down the send throws, the transaction rolls back, the rows stay pending and
 * the next tick retries — at-least-once, which is why consumers are idempotent.
 *
 * <p>Ordering: {@code ORDER BY id} is time order (UUIDv7). With ONE poller that keeps per-aggregate
 * order; with two replicas, SKIP LOCKED lets a newer row overtake an older one still being sent
 * (documented risk, absorbed by the saga guards).
 */
@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "publisher", havingValue = "polling", matchIfMissing = true)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;
    private final TraceContextCarrier trace;
    private final Timer publishLatency;
    private final AtomicLong pending = new AtomicLong();

    public OutboxPoller(JdbcClient jdbc, KafkaTemplate<String, String> kafka, OutboxProperties properties,
                        TraceContextCarrier trace, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.properties = properties;
        this.trace = trace;
        this.publishLatency = registry.timer("outbox.publish_latency");
        registry.gauge("outbox.pending", pending);
    }

    record PendingRow(UUID id, String aggregateType, String aggregateId, String type, String payload,
                      Instant createdAt, String traceparent) {
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:500ms}")
    @Transactional
    public void publishBatch() {
        List<PendingRow> rows = jdbc.sql("select id, aggregatetype, aggregateid, type, payload, created_at, traceparent from outbox "
                        + "where published_at is null order by id limit :batch for update skip locked")
                .param("batch", properties.batchSize())
                .query((rs, i) -> new PendingRow(rs.getObject("id", UUID.class), rs.getString("aggregatetype"),
                        rs.getString("aggregateid"), rs.getString("type"), rs.getString("payload"),
                        rs.getTimestamp("created_at").toInstant(), rs.getString("traceparent")))
                .list();
        for (PendingRow row : rows) {
            publish(row);
        }
        pending.set(countPending());
    }

    private void publish(PendingRow row) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(row.aggregateType() + ".events.v1", row.aggregateId(), row.payload());
        record.headers()
                .add(EventHeaders.EVENT_TYPE, row.type().getBytes(StandardCharsets.UTF_8))
                .add(EventHeaders.EVENT_ID, row.id().toString().getBytes(StandardCharsets.UTF_8));
        try {
            // continue the writer's trace: the KafkaTemplate observation becomes its child and puts a
            // matching traceparent header on the record, so the consumer's span joins the same trace
            trace.inContextOf(row.traceparent(), "outbox publish",
                    () -> kafka.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            // rollback: nothing is marked published, the batch is retried on the next tick
            throw new IllegalStateException("outbox publish failed for " + row.type() + " " + row.id(), e);
        }
        jdbc.sql("update outbox set published_at = :now where id = :id")
                .param("now", Timestamp.from(Instant.now()))
                .param("id", row.id())
                .update();
        publishLatency.record(Duration.between(row.createdAt(), Instant.now()));
        log.debug("outbox published {} {} -> {}", row.type(), row.id(), record.topic());
    }

    private long countPending() {
        return jdbc.sql("select count(*) from outbox where published_at is null").query(Long.class).single();
    }
}
