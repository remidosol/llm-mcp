package com.remidosol.llmmcp.job.application;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.event.CreditRejected;
import com.remidosol.llmmcp.contracts.event.CreditReserved;
import com.remidosol.llmmcp.contracts.event.LlmFailed;
import com.remidosol.llmmcp.contracts.event.LlmStarted;
import com.remidosol.llmmcp.contracts.event.LlmSucceeded;
import com.remidosol.llmmcp.job.application.port.JobRepository;
import com.remidosol.llmmcp.job.application.port.JobResultRepository;
import com.remidosol.llmmcp.job.domain.Job;
import com.remidosol.llmmcp.job.domain.JobResult;
import com.remidosol.llmmcp.job.domain.JobStatus;
import com.remidosol.llmmcp.job.domain.SagaPath;
import com.remidosol.llmmcp.job.domain.event.JobCompletedEvent;
import com.remidosol.llmmcp.job.domain.event.JobFailedEvent;
import com.remidosol.llmmcp.job.domain.event.JobRejectedEvent;
import com.remidosol.llmmcp.job.domain.event.JobTimedOutEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The choreography saga's view from job-service (PRD §4.2): every incoming event is a proposed
 * transition. The transition table decides; implied intermediate steps are walked ({@link SagaPath});
 * rejected transitions are logged, counted ({@code saga.guard_rejected}) and IGNORED — no exception,
 * so nothing is retried or dead-lettered for what is merely out-of-order delivery. Each handler is
 * one transaction shared with the inbox claim and the outbox row (ADR-0007, ADR-0009).
 */
@Service
public class SagaService {

    private static final Logger log = LoggerFactory.getLogger(SagaService.class);

    private final JobRepository jobs;
    private final JobResultRepository results;
    private final JobCacheEvictor cache;
    private final ApplicationEventPublisher events;
    private final MeterRegistry metrics;

    public SagaService(JobRepository jobs, JobResultRepository results, JobCacheEvictor cache,
                       ApplicationEventPublisher events, MeterRegistry metrics) {
        this.jobs = jobs;
        this.results = results;
        this.cache = cache;
        this.events = events;
        this.metrics = metrics;
    }

    @Transactional
    public void onCreditReserved(EventEnvelope trigger) {
        CreditReserved payload = trigger.payloadAs(CreditReserved.class);
        advance(payload.jobId(), JobStatus.CREDIT_RESERVED, job -> { });
    }

    @Transactional
    public void onCreditRejected(EventEnvelope trigger) {
        CreditRejected payload = trigger.payloadAs(CreditRejected.class);
        advance(payload.jobId(), JobStatus.REJECTED, job -> {
            job.recordFailure(payload.reason());
            events.publishEvent(new JobRejectedEvent(job.getId(), payload.reason(), trigger.eventId()));
        });
    }

    @Transactional
    public void onLlmStarted(EventEnvelope trigger) {
        LlmStarted payload = trigger.payloadAs(LlmStarted.class);
        advance(payload.jobId(), JobStatus.PROCESSING, job -> { });
    }

    @Transactional
    public void onLlmSucceeded(EventEnvelope trigger) {
        LlmSucceeded payload = trigger.payloadAs(LlmSucceeded.class);
        Optional<Job> found = jobs.findById(payload.jobId());
        if (found.isEmpty()) {
            log.warn("LlmSucceeded for unknown job {}", payload.jobId());
            return;
        }
        Job job = found.get();
        if (job.getStatus() == JobStatus.TIMED_OUT) {
            // Late result (ADR-0016): keep the output, do not reopen the job, do not re-capture credits.
            storeResult(job, payload, true);
            metrics.counter("saga.late_result").increment();
            log.warn("late LlmSucceeded for timed-out job {} stored with late=true", job.getId());
            return;
        }
        advance(job, JobStatus.COMPLETED, j -> {
            j.recordActualCredits(payload.actualCredits());
            storeResult(j, payload, false);
            events.publishEvent(new JobCompletedEvent(j.getId(), j.getUserId(), payload.actualCredits(), trigger.eventId()));
        });
    }

    @Transactional
    public void onLlmFailed(EventEnvelope trigger) {
        LlmFailed payload = trigger.payloadAs(LlmFailed.class);
        advance(payload.jobId(), JobStatus.FAILED, job -> {
            job.recordFailure(payload.reason());
            events.publishEvent(new JobFailedEvent(job.getId(), job.getUserId(), payload.reason(), trigger.eventId()));
        });
    }

    /** Called by the watchdog with a row it already locked. */
    @Transactional
    public void timeOut(Job job) {
        advance(job, JobStatus.TIMED_OUT, j -> {
            j.recordFailure("timed out after the saga deadline");
            events.publishEvent(new JobTimedOutEvent(j.getId(), j.getUserId()));
        });
    }

    private void advance(UUID jobId, JobStatus target, Consumer<Job> effect) {
        jobs.findById(jobId).ifPresentOrElse(
                job -> advance(job, target, effect),
                () -> log.warn("saga event {} for unknown job {} ignored", target, jobId));
    }

    private void advance(Job job, JobStatus target, Consumer<Job> effect) {
        JobStatus from = job.getStatus();
        Optional<List<JobStatus>> path = SagaPath.impliedPath(from, target);
        if (path.isEmpty()) {
            metrics.counter("saga.guard_rejected", "from", from.name(), "to", target.name()).increment();
            log.warn("guard rejected {} -> {} for job {} (ignored)", from, target, job.getId());
            return;
        }
        JobStatus current = from;
        for (JobStatus step : path.get()) {
            job.transitionTo(step);
            metrics.counter("saga.transition", "from", current.name(), "to", step.name()).increment();
            current = step;
        }
        effect.accept(job);
        cache.evict(job.getId());
    }

    private void storeResult(Job job, LlmSucceeded payload, boolean late) {
        if (results.existsById(job.getId())) {
            return;
        }
        results.save(JobResult.of(job.getId(), payload.output(), payload.provider(), payload.model(),
                payload.promptTokens(), payload.completionTokens(), late));
    }
}
