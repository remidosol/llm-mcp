package com.remidosol.llmmcp.job.application.port;

import com.remidosol.llmmcp.job.domain.Job;
import com.remidosol.llmmcp.job.domain.JobStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for job persistence. It exists so the application layer never names an
 * infrastructure type: the arrow of dependency points inward (hexagonal-lite, ADR-0003), and
 * ArchUnit enforces it. The infrastructure adapter implements this interface by extending both
 * it and Spring Data's {@code JpaRepository}.
 */
public interface JobRepository {

    Job save(Job job);

    Optional<Job> findById(UUID id);

    /** Newest first for the given user; {@code status} may be null (no filter). */
    List<Job> findForUser(String userId, JobStatus status, int limit);

    /**
     * Jobs stuck in {@code statuses} since before {@code cutoff}, locked with
     * {@code FOR UPDATE SKIP LOCKED} so several watchdog replicas never time out the same job twice.
     */
    List<Job> findStuckForUpdate(Collection<JobStatus> statuses, Instant cutoff, int limit);
}
