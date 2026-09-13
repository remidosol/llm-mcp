package com.remidosol.llmmcp.job.domain.event;

import java.util.UUID;

/** Domain event raised by the watchdog; credit-service releases the hold (compensation). */
public record JobTimedOutEvent(UUID jobId, String userId) {
}
