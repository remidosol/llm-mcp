package com.remidosol.llmmcp.llm.domain.event;

import java.util.UUID;

/** Domain event: a provider call is about to start (moves the job to PROCESSING). */
public record LlmStartedEvent(UUID jobId, String provider, String model, int attemptNo, UUID causationId) {
}
