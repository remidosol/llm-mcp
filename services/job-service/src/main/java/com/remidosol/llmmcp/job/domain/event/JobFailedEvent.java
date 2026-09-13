package com.remidosol.llmmcp.job.domain.event;

import java.util.UUID;

/** Domain event: the LLM step failed for good; credit-service releases the hold (compensation). */
public record JobFailedEvent(UUID jobId, String userId, String reason, UUID causationId) {
}
