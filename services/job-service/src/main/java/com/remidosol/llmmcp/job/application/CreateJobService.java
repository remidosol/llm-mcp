package com.remidosol.llmmcp.job.application;

import com.remidosol.llmmcp.job.application.port.JobRepository;
import com.remidosol.llmmcp.job.domain.Job;
import com.remidosol.llmmcp.job.domain.event.JobCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The "submit a job" use case. It exists as the single transaction boundary for job creation:
 * in Phase 3 the outbox row (JobCreated) will be inserted in THIS SAME transaction — that
 * placement is the whole point of the transactional outbox, so the boundary lives here, not in
 * the controller.
 */
@Service
public class CreateJobService {

    private final JobRepository jobRepository;
    private final CreditEstimator creditEstimator;
    private final ApplicationEventPublisher events;

    public CreateJobService(JobRepository jobRepository, CreditEstimator creditEstimator,
                            ApplicationEventPublisher events) {
        this.jobRepository = jobRepository;
        this.creditEstimator = creditEstimator;
        this.events = events;
    }

    @Transactional
    public JobView create(String userId, String prompt, String model) {
        // Plain random UUID for now; time-ordered UUIDv7 arrives with the event envelope (Phase 2).
        UUID id = UUID.randomUUID();
        int estimatedCredits = creditEstimator.estimate(prompt, model);
        Job job = Job.create(id, userId, prompt, model, estimatedCredits);
        jobRepository.save(job);
        // The domain event is published in-process; WHO turns it into a Kafka record is an
        // infrastructure decision: Phase 2 sends after commit (deliberate dual write), Phase 3
        // writes an outbox row inside this same transaction.
        events.publishEvent(new JobCreatedEvent(id, userId, prompt, model, estimatedCredits));
        return JobView.from(job);
    }
}
