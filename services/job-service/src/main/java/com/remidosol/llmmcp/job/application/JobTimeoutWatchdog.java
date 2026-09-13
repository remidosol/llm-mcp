package com.remidosol.llmmcp.job.application;

import com.remidosol.llmmcp.job.application.port.JobRepository;
import com.remidosol.llmmcp.job.domain.Job;
import com.remidosol.llmmcp.job.domain.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Saga timeouts (PRD §4.2, ADR-0016): a job that sits in CREDIT_RESERVED or PROCESSING longer than
 * {@code app.saga.job-timeout} is moved to TIMED_OUT and a {@code JobTimedOut} event releases the
 * credits. The batch is claimed with {@code FOR UPDATE SKIP LOCKED}, so replicas never race.
 */
@Component
public class JobTimeoutWatchdog {

    private static final Logger log = LoggerFactory.getLogger(JobTimeoutWatchdog.class);
    private static final int BATCH = 100;

    private final JobRepository jobs;
    private final SagaService saga;
    private final SagaProperties properties;

    public JobTimeoutWatchdog(JobRepository jobs, SagaService saga, SagaProperties properties) {
        this.jobs = jobs;
        this.saga = saga;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.saga.watchdog-interval:30s}")
    @Transactional
    public void sweep() {
        Instant cutoff = Instant.now().minus(properties.jobTimeout());
        List<Job> stuck = jobs.findStuckForUpdate(EnumSet.of(JobStatus.CREDIT_RESERVED, JobStatus.PROCESSING), cutoff, BATCH);
        for (Job job : stuck) {
            saga.timeOut(job);
        }
        if (!stuck.isEmpty()) {
            log.warn("watchdog timed out {} job(s) older than {}", stuck.size(), properties.jobTimeout());
        }
    }
}
