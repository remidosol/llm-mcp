package com.remidosol.llmmcp.contracts.event;

import java.util.UUID;

/** Emitted by job-service after CreditRejected moved the job to REJECTED. */
public record JobRejected(UUID jobId, String reason) {
}
