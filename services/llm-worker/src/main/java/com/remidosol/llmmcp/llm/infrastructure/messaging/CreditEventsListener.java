package com.remidosol.llmmcp.llm.infrastructure.messaging;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.Topics;
import com.remidosol.llmmcp.llm.application.ExecuteLlmJobService;
import com.remidosol.llmmcp.llm.infrastructure.inbox.IdempotentHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/** Inbound adapter: {@code CreditReserved} is the worker's only trigger; everything else is skipped. */
@Component
class CreditEventsListener {

    private static final Logger log = LoggerFactory.getLogger(CreditEventsListener.class);

    private final IdempotentHandler inbox;
    private final ExecuteLlmJobService execute;

    CreditEventsListener(IdempotentHandler inbox, ExecuteLlmJobService execute) {
        this.inbox = inbox;
        this.execute = execute;
    }

    @KafkaListener(topics = Topics.CREDIT_EVENTS, groupId = "${spring.kafka.consumer.group-id:llm-worker}")
    public void on(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = parse(record);
        if (EventTypes.CREDIT_RESERVED.equals(envelope.eventType())) {
            inbox.handle(envelope, ConsumerGroups.LLM_WORKER, () -> execute.execute(envelope));
        } else {
            log.debug("ignoring event type {} ({}) on {}", envelope.eventType(), envelope.eventId(), record.topic());
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
