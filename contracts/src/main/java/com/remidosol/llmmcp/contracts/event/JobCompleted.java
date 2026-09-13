package com.remidosol.llmmcp.contracts.event;

import java.util.UUID;

/** Emitted by job-service on COMPLETED; credit-service captures the actual cost. */
public record JobCompleted(UUID jobId, String userId, int actualCredits) {
}
