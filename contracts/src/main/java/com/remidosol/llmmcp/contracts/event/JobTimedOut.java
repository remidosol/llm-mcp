package com.remidosol.llmmcp.contracts.event;

import java.util.UUID;

/** Emitted by the job-service watchdog; credit-service releases the reservation. */
public record JobTimedOut(UUID jobId, String userId) {
}
