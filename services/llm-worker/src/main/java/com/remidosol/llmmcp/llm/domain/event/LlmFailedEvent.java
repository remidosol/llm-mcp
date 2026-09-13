package com.remidosol.llmmcp.llm.domain.event;

import java.util.UUID;

/** Domain event: the provider call failed for good (after any retries). */
public record LlmFailedEvent(UUID jobId, String provider, String model, String reason, boolean retryable,
                             int attempts, UUID causationId) {
}
