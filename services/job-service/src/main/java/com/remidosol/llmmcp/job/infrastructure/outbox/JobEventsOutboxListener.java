package com.remidosol.llmmcp.job.infrastructure.outbox;

import com.remidosol.llmmcp.contracts.AggregateTypes;
import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.event.JobCompleted;
import com.remidosol.llmmcp.contracts.event.JobCreated;
import com.remidosol.llmmcp.contracts.event.JobFailed;
import com.remidosol.llmmcp.contracts.event.JobRejected;
import com.remidosol.llmmcp.contracts.event.JobTimedOut;
import com.remidosol.llmmcp.job.domain.event.JobCompletedEvent;
import com.remidosol.llmmcp.job.domain.event.JobCreatedEvent;
import com.remidosol.llmmcp.job.domain.event.JobFailedEvent;
import com.remidosol.llmmcp.job.domain.event.JobRejectedEvent;
import com.remidosol.llmmcp.job.domain.event.JobTimedOutEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Maps job domain events to contract envelopes and appends them to the outbox. A plain
 * {@code @EventListener} runs synchronously inside the publishing transaction — that is the whole
 * trick: if the job insert commits, the outbox row commits; if either fails, neither exists.
 * (An {@code @TransactionalEventListener(AFTER_COMMIT)} publisher would be the dual-write bug.)
 */
@Component
class JobEventsOutboxListener {

    static final String PRODUCER = "job-service";

    private final OutboxWriter outbox;

    JobEventsOutboxListener(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @EventListener
    public void on(JobCreatedEvent event) {
        String jobId = event.jobId().toString();
        outbox.append(EventEnvelope.create(EventTypes.JOB_CREATED, AggregateTypes.JOB, jobId, jobId, null, PRODUCER,
                new JobCreated(event.jobId(), event.userId(), event.prompt(), event.model(), event.estimatedCredits())));
    }

    @EventListener
    public void on(JobRejectedEvent event) {
        String jobId = event.jobId().toString();
        outbox.append(EventEnvelope.create(EventTypes.JOB_REJECTED, AggregateTypes.JOB, jobId, jobId,
                event.causationId(), PRODUCER, new JobRejected(event.jobId(), event.reason())));
    }

    @EventListener
    public void on(JobCompletedEvent event) {
        String jobId = event.jobId().toString();
        outbox.append(EventEnvelope.create(EventTypes.JOB_COMPLETED, AggregateTypes.JOB, jobId, jobId,
                event.causationId(), PRODUCER, new JobCompleted(event.jobId(), event.userId(), event.actualCredits())));
    }

    @EventListener
    public void on(JobFailedEvent event) {
        String jobId = event.jobId().toString();
        outbox.append(EventEnvelope.create(EventTypes.JOB_FAILED, AggregateTypes.JOB, jobId, jobId,
                event.causationId(), PRODUCER, new JobFailed(event.jobId(), event.userId(), event.reason())));
    }

    @EventListener
    public void on(JobTimedOutEvent event) {
        String jobId = event.jobId().toString();
        outbox.append(EventEnvelope.create(EventTypes.JOB_TIMED_OUT, AggregateTypes.JOB, jobId, jobId,
                null, PRODUCER, new JobTimedOut(event.jobId(), event.userId())));
    }
}
