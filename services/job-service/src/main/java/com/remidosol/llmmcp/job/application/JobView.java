package com.remidosol.llmmcp.job.application;

import com.remidosol.llmmcp.job.domain.Job;
import com.remidosol.llmmcp.job.domain.JobStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model returned by queries and stored in the Redis cache. It exists because caching the JPA
 * entity is a trap (lazy proxies, session-bound state, serialization surprises) — the cache holds
 * this flat, {@link Serializable} record instead. Living in the application layer keeps the
 * dependency arrow clean: api depends on application, never the other way around.
 */
public record JobView(
        UUID jobId,
        String userId,
        String model,
        JobStatus status,
        int estimatedCredits,
        Integer actualCredits,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public static JobView from(Job job) {
        return new JobView(
                job.getId(),
                job.getUserId(),
                job.getModel(),
                job.getStatus(),
                job.getEstimatedCredits(),
                job.getActualCredits(),
                job.getFailureReason(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
