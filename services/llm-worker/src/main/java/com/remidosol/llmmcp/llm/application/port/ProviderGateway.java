package com.remidosol.llmmcp.llm.application.port;

import com.remidosol.llmmcp.llm.domain.LlmResult;

/**
 * Port between the use case and "how carefully do we call a provider": the implementation adds
 * rate limiting, retries and a circuit breaker (Phase 5) without the use case knowing.
 */
public interface ProviderGateway {

    /** @param tries how many provider calls this outcome cost (1 = first try succeeded) */
    record Outcome(LlmResult result, int tries) {
    }

    Outcome call(LlmProvider provider, String model, String prompt, int maxTokens);
}
