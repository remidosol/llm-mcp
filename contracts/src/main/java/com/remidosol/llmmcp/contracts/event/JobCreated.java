package com.remidosol.llmmcp.contracts.event;

import java.util.UUID;

/** Emitted by job-service when a job is accepted; starts the saga (credit reservation). */
public record JobCreated(UUID jobId, String userId, String prompt, String model, int estimatedCredits) {
}
