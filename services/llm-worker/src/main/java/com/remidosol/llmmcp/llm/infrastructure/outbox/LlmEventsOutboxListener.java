package com.remidosol.llmmcp.llm.infrastructure.outbox;

import com.remidosol.llmmcp.contracts.AggregateTypes;
import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.event.LlmFailed;
import com.remidosol.llmmcp.contracts.event.LlmStarted;
import com.remidosol.llmmcp.contracts.event.LlmSucceeded;
import com.remidosol.llmmcp.llm.domain.event.LlmFailedEvent;
import com.remidosol.llmmcp.llm.domain.event.LlmStartedEvent;
import com.remidosol.llmmcp.llm.domain.event.LlmSucceededEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Maps worker domain events to contract envelopes inside the publishing transaction. */
@Component
class LlmEventsOutboxListener {

    static final String PRODUCER = "llm-worker";

    private final OutboxWriter outbox;

    LlmEventsOutboxListener(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @EventListener
    public void on(LlmStartedEvent event) {
        String jobId = event.jobId().toString();
        outbox.append(EventEnvelope.create(EventTypes.LLM_STARTED, AggregateTypes.LLM, jobId, jobId, event.causationId(),
                PRODUCER, new LlmStarted(event.jobId(), event.provider(), event.model(), event.attemptNo())));
    }

    @EventListener
    public void on(LlmSucceededEvent event) {
        String jobId = event.jobId().toString();
        outbox.append(EventEnvelope.create(EventTypes.LLM_SUCCEEDED, AggregateTypes.LLM, jobId, jobId, event.causationId(),
                PRODUCER, new LlmSucceeded(event.jobId(), event.provider(), event.model(), event.output(),
                        event.promptTokens(), event.completionTokens(), event.actualCredits())));
    }

    @EventListener
    public void on(LlmFailedEvent event) {
        String jobId = event.jobId().toString();
        outbox.append(EventEnvelope.create(EventTypes.LLM_FAILED, AggregateTypes.LLM, jobId, jobId, event.causationId(),
                PRODUCER, new LlmFailed(event.jobId(), event.provider(), event.model(), event.reason(),
                        event.retryable(), event.attempts())));
    }
}
