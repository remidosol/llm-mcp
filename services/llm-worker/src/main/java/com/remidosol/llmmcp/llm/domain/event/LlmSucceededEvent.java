package com.remidosol.llmmcp.llm.domain.event;

import java.util.UUID;

/** Domain event: output and measured cost for a job. */
public record LlmSucceededEvent(UUID jobId, String provider, String model, String output, int promptTokens,
                                int completionTokens, int actualCredits, UUID causationId) {
}
