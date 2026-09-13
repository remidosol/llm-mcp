package com.remidosol.llmmcp.llm.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/** {@code app.credits.*}: the same multipliers job-service uses for the estimate (PRD §4.4). */
@ConfigurationProperties(prefix = "app.credits")
public record CreditProperties(int defaultMultiplier, Map<String, Integer> multipliers) {
}
