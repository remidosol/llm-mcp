package com.remidosol.llmmcp.credit.infrastructure.messaging;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.Topics;
import com.remidosol.llmmcp.contracts.event.JobFailed;
import com.remidosol.llmmcp.contracts.event.JobTimedOut;
import com.remidosol.llmmcp.credit.application.ReserveCreditService;
import com.remidosol.llmmcp.credit.application.SettleCreditService;
import com.remidosol.llmmcp.credit.infrastructure.inbox.IdempotentHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * Inbound Kafka adapter for {@code job.events.v1}. Parses the envelope, dispatches on the
 * eventType STRING (unknown types are logged and skipped, PRD §4.3) and runs every handler through
 * the inbox. Offsets are committed after this method returns (at-least-once).
 */
@Component
class JobEventsListener {

    private static final Logger log = LoggerFactory.getLogger(JobEventsListener.class);

    private final IdempotentHandler inbox;
    private final ReserveCreditService reserveCredit;
    private final SettleCreditService settleCredit;

    JobEventsListener(IdempotentHandler inbox, ReserveCreditService reserveCredit, SettleCreditService settleCredit) {
        this.inbox = inbox;
        this.reserveCredit = reserveCredit;
        this.settleCredit = settleCredit;
    }

    // Kafka group id is configurable (replay into a fresh group); the inbox key stays the LOGICAL name
    @KafkaListener(topics = Topics.JOB_EVENTS, groupId = "${spring.kafka.consumer.group-id:credit-service}")
    public void on(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = parse(record);
        String group = ConsumerGroups.CREDIT_SERVICE;
        switch (envelope.eventType()) {
            case EventTypes.JOB_CREATED -> inbox.handle(envelope, group, () -> reserveCredit.reserve(envelope));
            case EventTypes.JOB_COMPLETED -> inbox.handle(envelope, group, () -> settleCredit.capture(envelope));
            case EventTypes.JOB_FAILED -> inbox.handle(envelope, group,
                    () -> settleCredit.release(envelope.payloadAs(JobFailed.class).jobId(), envelope));
            case EventTypes.JOB_TIMED_OUT -> inbox.handle(envelope, group,
                    () -> settleCredit.release(envelope.payloadAs(JobTimedOut.class).jobId(), envelope));
            default -> log.debug("ignoring event type {} ({}) on {}", envelope.eventType(), envelope.eventId(),
                    record.topic());
        }
    }

    private static EventEnvelope parse(ConsumerRecord<String, String> record) {
        try {
            return EventEnvelope.fromJson(record.value());
        } catch (JacksonException | NullPointerException e) {
            // poison message: no retry will ever make it parse — straight to the DLT
            throw new NonRetryableException("unparseable event at %s-%d@%d".formatted(
                    record.topic(), record.partition(), record.offset()), e);
        }
    }
}
