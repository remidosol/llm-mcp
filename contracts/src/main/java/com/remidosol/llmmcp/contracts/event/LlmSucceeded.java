package com.remidosol.llmmcp.contracts.event;

import java.util.UUID;

/** Emitted by llm-worker with the model output and the measured cost. */
public record LlmSucceeded(UUID jobId, String provider, String model, String output,
                           int promptTokens, int completionTokens, int actualCredits) {
}
