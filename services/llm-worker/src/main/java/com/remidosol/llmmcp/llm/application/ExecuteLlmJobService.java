package com.remidosol.llmmcp.llm.application;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.event.CreditReserved;
import com.remidosol.llmmcp.llm.application.port.LlmProvider;
import com.remidosol.llmmcp.llm.application.port.ProviderGateway;
import com.remidosol.llmmcp.llm.domain.LlmAttempt;
import com.remidosol.llmmcp.llm.domain.LlmCallException;
import com.remidosol.llmmcp.llm.domain.LlmResult;
import com.remidosol.llmmcp.llm.domain.event.LlmFailedEvent;
import com.remidosol.llmmcp.llm.domain.event.LlmSucceededEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga step 4: run the prompt for a reserved job. Runs INSIDE the inbox transaction, so
 * the event claim, the attempt outcome and the outbox row commit together — a crash mid-call rolls
 * the claim back and the redelivered event produces a fresh attempt (attemptNo + 1). The provider
 * is chosen by model prefix (Strategy); the worker never talks to job-service: prompt and model
 * arrive on the event (ADR-0010).
 */
@Service
public class ExecuteLlmJobService {

    private static final Logger log = LoggerFactory.getLogger(ExecuteLlmJobService.class);

    private final LlmProviders providers;
    private final ProviderGateway gateway;
    private final AttemptRecorder recorder;
    private final CreditCalculator credits;
    private final LlmProperties properties;
    private final ApplicationEventPublisher events;
    private final MeterRegistry metrics;

    public ExecuteLlmJobService(LlmProviders providers, ProviderGateway gateway, AttemptRecorder recorder,
                                CreditCalculator credits, LlmProperties properties, ApplicationEventPublisher events,
                                MeterRegistry metrics) {
        this.providers = providers;
        this.gateway = gateway;
        this.recorder = recorder;
        this.credits = credits;
        this.properties = properties;
        this.events = events;
        this.metrics = metrics;
    }

    @Transactional
    public void execute(EventEnvelope trigger) {
        CreditReserved reserved = trigger.payloadAs(CreditReserved.class);
        LlmProviders.ModelRef ref;
        LlmProvider provider;
        try {
            ref = LlmProviders.ModelRef.parse(reserved.model());
            provider = providers.forModel(ref);
        } catch (LlmCallException e) {
            events.publishEvent(new LlmFailedEvent(reserved.jobId(), "none", reserved.model(), e.getMessage(), false, 0,
                    trigger.eventId()));
            return;
        }

        LlmAttempt attempt = recorder.start(reserved.jobId(), ref.provider(), ref.modelId(), trigger.eventId());
        Timer.Sample sample = Timer.start(metrics);
        try {
            ProviderGateway.Outcome outcome = gateway.call(provider, ref.modelId(), reserved.prompt(), properties.maxTokens());
            LlmResult result = outcome.result();
            sample.stop(metrics.timer("llm.call", "provider", ref.provider(), "outcome", "success"));
            metrics.counter("llm.tokens", "provider", ref.provider(), "kind", "prompt").increment(result.promptTokens());
            metrics.counter("llm.tokens", "provider", ref.provider(), "kind", "completion").increment(result.completionTokens());
            int actual = credits.actualCredits(ref.provider(), result.promptTokens(), result.completionTokens());
            recorder.succeed(attempt.getId(), result);
            events.publishEvent(new LlmSucceededEvent(reserved.jobId(), ref.provider(), ref.modelId(), result.output(),
                    result.promptTokens(), result.completionTokens(), actual, trigger.eventId()));
        } catch (LlmCallException e) {
            sample.stop(metrics.timer("llm.call", "provider", ref.provider(), "outcome", "failure"));
            log.warn("provider {} failed for job {} (delivery {}, tries {}, retryable={}): {}", ref.provider(),
                    reserved.jobId(), attempt.getAttemptNo(), e.tries(), e.isRetryable(), e.getMessage());
            recorder.fail(attempt.getId(), e.getMessage());
            events.publishEvent(new LlmFailedEvent(reserved.jobId(), ref.provider(), ref.modelId(), e.getMessage(),
                    e.isRetryable(), e.tries(), trigger.eventId()));
        }
    }
}
