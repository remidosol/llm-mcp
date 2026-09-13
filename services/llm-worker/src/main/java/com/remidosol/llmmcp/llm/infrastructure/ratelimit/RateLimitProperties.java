package com.remidosol.llmmcp.llm.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/** {@code app.llm.ratelimit.*}: one token bucket per provider; unknown providers are not limited. */
@ConfigurationProperties(prefix = "app.llm.ratelimit")
public record RateLimitProperties(Duration maxWait, Map<String, Bucket> providers) {

    public record Bucket(int capacity, double refillPerSecond) {
    }
}
