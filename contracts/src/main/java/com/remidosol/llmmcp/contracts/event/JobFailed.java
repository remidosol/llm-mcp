package com.remidosol.llmmcp.contracts.event;

import java.util.UUID;

/** Emitted by job-service on FAILED; credit-service releases the reservation (compensation). */
public record JobFailed(UUID jobId, String userId, String reason) {
}
