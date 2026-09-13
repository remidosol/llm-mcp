package com.remidosol.llmmcp.llm.domain;

/** What a provider returns: the text and the token usage the cost is computed from. */
public record LlmResult(String output, int promptTokens, int completionTokens) {
}
