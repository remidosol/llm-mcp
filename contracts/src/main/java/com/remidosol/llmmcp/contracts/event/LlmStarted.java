package com.remidosol.llmmcp.contracts.event;

import java.util.UUID;

/** Emitted by llm-worker before calling the provider; moves the job to PROCESSING. */
public record LlmStarted(UUID jobId, String provider, String model, int attemptNo) {
}
