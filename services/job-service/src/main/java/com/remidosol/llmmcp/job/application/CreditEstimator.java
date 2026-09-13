package com.remidosol.llmmcp.job.application;

import org.springframework.stereotype.Component;

/**
 * Deliberately simplistic credit estimation: {@code max(1, ceil(len/4) * multiplier)}.
 * It exists as its own component so the estimate has one home, one unit test, and one place to
 * swap for a token-count-based model later — the saga only ever sees a number.
 */
@Component
public class CreditEstimator {

    private final CreditProperties properties;

    public CreditEstimator(CreditProperties properties) {
        this.properties = properties;
    }

    public int estimate(String prompt, String model) {
        int multiplier = multiplierFor(model);
        int base = (int) Math.ceil(prompt.length() / 4.0);
        return Math.max(1, base * multiplier);
    }

    private int multiplierFor(String model) {
        String prefix = model.contains(":") ? model.substring(0, model.indexOf(':')) : model;
        return properties.multipliers().getOrDefault(prefix, properties.defaultMultiplier());
    }
}
