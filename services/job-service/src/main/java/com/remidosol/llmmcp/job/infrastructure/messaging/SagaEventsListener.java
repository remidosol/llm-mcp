package com.remidosol.llmmcp.job.infrastructure.messaging;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.Topics;
import com.remidosol.llmmcp.job.application.SagaService;
import com.remidosol.llmmcp.job.infrastructure.inbox.IdempotentHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * Inbound adapter for the two topics job-service reacts to. Cross-topic ordering is not
 * guaranteed (ADR-0006): the saga service, not this listener, decides what an event means.
 */
@Component
class SagaEventsListener {

    private static final Logger log = LoggerFactory.getLogger(SagaEventsListener.class);

    private final IdempotentHandler inbox;
    private final SagaService saga;

    SagaEventsListener(IdempotentHandler inbox, SagaService saga) {
        this.inbox = inbox;
        this.saga = saga;
    }

    @KafkaListener(topics = {Topics.CREDIT_EVENTS, Topics.LLM_EVENTS}, groupId = "${spring.kafka.consumer.group-id:job-service}")
    public void on(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = parse(record);
        String group = ConsumerGroups.JOB_SERVICE;
        switch (envelope.eventType()) {
            case EventTypes.CREDIT_RESERVED -> inbox.handle(envelope, group, () -> saga.onCreditReserved(envelope));
            case EventTypes.CREDIT_REJECTED -> inbox.handle(envelope, group, () -> saga.onCreditRejected(envelope));
            case EventTypes.LLM_STARTED -> inbox.handle(envelope, group, () -> saga.onLlmStarted(envelope));
            case EventTypes.LLM_SUCCEEDED -> inbox.handle(envelope, group, () -> saga.onLlmSucceeded(envelope));
            case EventTypes.LLM_FAILED -> inbox.handle(envelope, group, () -> saga.onLlmFailed(envelope));
            default -> log.debug("ignoring event type {} ({}) on {}", envelope.eventType(), envelope.eventId(),
                    record.topic());
        }
    }

    private static EventEnvelope parse(ConsumerRecord<String, String> record) {
        try {
            return EventEnvelope.fromJson(record.value());
        } catch (JacksonException | NullPointerException e) {
            throw new NonRetryableException("unparseable event at %s-%d@%d".formatted(
                    record.topic(), record.partition(), record.offset()), e);
        }
    }
}
