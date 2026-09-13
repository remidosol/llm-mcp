package com.remidosol.llmmcp.job.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Typed view of {@code app.credits.*}. Exists so credit math constants never hide as string
 * lookups in code: the record is immutable, bound once at startup, and IDE-discoverable
 * (the configuration processor generates metadata for it).
 *
 * @param defaultMultiplier used when a model prefix has no entry in {@link #multipliers()}
 * @param multipliers       per-provider multiplier keyed by model prefix (fake/openai/gemini)
 */
@ConfigurationProperties(prefix = "app.credits")
public record CreditProperties(int defaultMultiplier, Map<String, Integer> multipliers) {
}
