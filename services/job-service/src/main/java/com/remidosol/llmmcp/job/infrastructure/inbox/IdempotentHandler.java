package com.remidosol.llmmcp.job.infrastructure.inbox;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The idempotent-consumer template (PRD §4.8, ADR-0007): every listener runs its handler through
 * this method. The invariant steps — claim the event id in {@code processed_event}, run the
 * handler, commit both together — are fixed here; only the handler varies (Template Method, in
 * functional form). Kafka is at-least-once, so redelivery is normal: the second delivery hits
 * {@code ON CONFLICT DO NOTHING}, inserts nothing, and the handler is skipped. Because the insert
 * shares the handler's transaction, a crash mid-handler rolls back the claim and the event is
 * processed again on redelivery — exactly once in effect, at-least-once on the wire.
 */
@Component
public class IdempotentHandler {

    private static final Logger log = LoggerFactory.getLogger(IdempotentHandler.class);

    private final JdbcClient jdbc;
    private final Counter duplicates;

    public IdempotentHandler(JdbcClient jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.duplicates = registry.counter("inbox.duplicate");
    }

    @Transactional
    public void handle(EventEnvelope envelope, String consumerGroup, Runnable handler) {
        // correlationId is the job id for every event in the saga (PRD §4.3): one MDC key tags every log line
        try (MDC.MDCCloseable ignored = MDC.putCloseable("jobId", envelope.correlationId())) {
            claimAndRun(envelope, consumerGroup, handler);
        }
    }

    private void claimAndRun(EventEnvelope envelope, String consumerGroup, Runnable handler) {
        int claimed = jdbc.sql("insert into processed_event (event_id, consumer_group) values (:eventId, :group) "
                        + "on conflict do nothing")
                .param("eventId", envelope.eventId())
                .param("group", consumerGroup)
                .update();
        if (claimed == 0) {
            duplicates.increment();
            log.info("duplicate delivery of {} {} for group {} — skipped", envelope.eventType(), envelope.eventId(),
                    consumerGroup);
            return;
        }
        handler.run();
    }
}
