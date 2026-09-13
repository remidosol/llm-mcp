package com.remidosol.llmmcp.contracts.event;

import java.util.UUID;

/** Emitted by llm-worker after retries are exhausted or on a non-retryable provider error. */
public record LlmFailed(UUID jobId, String provider, String model, String reason, boolean retryable, int attempts) {
}
