package com.remidosol.llmmcp.llm.application;

import com.remidosol.llmmcp.contracts.UuidV7;
import com.remidosol.llmmcp.llm.application.port.LlmAttemptRepository;
import com.remidosol.llmmcp.llm.domain.LlmAttempt;
import com.remidosol.llmmcp.llm.domain.LlmResult;
import com.remidosol.llmmcp.llm.domain.event.LlmStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists the attempt trail. {@link #start} runs in its OWN transaction ({@code REQUIRES_NEW}) so
 * {@code LlmStarted} reaches the outbox — and the job moves to PROCESSING — before the provider
 * call begins, even though the surrounding inbox transaction stays open until the outcome is known.
 */
@Service
public class AttemptRecorder {

    private final LlmAttemptRepository attempts;
    private final ApplicationEventPublisher events;

    public AttemptRecorder(LlmAttemptRepository attempts, ApplicationEventPublisher events) {
        this.attempts = attempts;
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LlmAttempt start(UUID jobId, String provider, String model, UUID causationId) {
        int attemptNo = (int) attempts.countByJobId(jobId) + 1;
        LlmAttempt attempt = attempts.save(LlmAttempt.start(UuidV7.generate(), jobId, provider, model, attemptNo));
        events.publishEvent(new LlmStartedEvent(jobId, provider, model, attemptNo, causationId));
        return attempt;
    }

    @Transactional
    public void succeed(UUID attemptId, LlmResult result) {
        attempts.findById(attemptId).orElseThrow().succeed(result);
    }

    @Transactional
    public void fail(UUID attemptId, String error) {
        attempts.findById(attemptId).orElseThrow().fail(error);
    }
}
