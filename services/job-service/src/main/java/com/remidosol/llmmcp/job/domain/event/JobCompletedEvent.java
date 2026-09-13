package com.remidosol.llmmcp.job.domain.event;

import java.util.UUID;

/** Domain event: the result is stored; credit-service will capture {@code actualCredits}. */
public record JobCompletedEvent(UUID jobId, String userId, int actualCredits, UUID causationId) {
}
