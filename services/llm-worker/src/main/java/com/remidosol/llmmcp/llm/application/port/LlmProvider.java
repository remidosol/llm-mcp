package com.remidosol.llmmcp.llm.application.port;

import com.remidosol.llmmcp.llm.domain.LlmResult;

/**
 * The Strategy port for LLM providers: the use case picks an implementation by the
 * model prefix ({@code openai:}, {@code gemini:}, {@code fake:}) and never sees SDK types.
 * Implementations throw {@link com.remidosol.llmmcp.llm.domain.LlmCallException} with a
 * retryable flag; everything else about failure handling lives outside the adapter.
 */
public interface LlmProvider {

    /** The prefix this provider owns, e.g. {@code "openai"}. */
    String name();

    LlmResult complete(String model, String prompt, int maxTokens, int attemptNo);
}
