package com.remidosol.llmmcp.job.domain.event;

import java.util.UUID;

/**
 * Domain event raised when a job is accepted. Plain record, no framework: the domain announces
 * what happened, and the infrastructure decides how it leaves the process (the outbox).
 * Keeping it in the domain lets the application layer stay ignorant of Kafka.
 */
public record JobCreatedEvent(UUID jobId, String userId, String prompt, String model, int estimatedCredits) {
}
