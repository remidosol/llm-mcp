package com.remidosol.llmmcp.llm.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code app.llm.*}: the output cap and the fake provider's knobs. */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(int maxTokens, Fake fake) {

    public record Fake(Duration latency, boolean failureInjection, Duration slowDelay) {
    }
}
