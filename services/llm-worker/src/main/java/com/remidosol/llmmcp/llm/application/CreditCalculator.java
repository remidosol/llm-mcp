package com.remidosol.llmmcp.llm.application;

import org.springframework.stereotype.Component;

/** actualCredits = max(1, ceil((promptTokens + completionTokens) / 10) * multiplier). */
@Component
public class CreditCalculator {

    private final CreditProperties properties;

    public CreditCalculator(CreditProperties properties) {
        this.properties = properties;
    }

    public int actualCredits(String provider, int promptTokens, int completionTokens) {
        int multiplier = properties.multipliers().getOrDefault(provider, properties.defaultMultiplier());
        int base = (int) Math.ceil((promptTokens + completionTokens) / 10.0);
        return Math.max(1, base * multiplier);
    }
}
