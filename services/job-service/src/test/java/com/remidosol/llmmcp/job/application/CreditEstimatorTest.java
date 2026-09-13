package com.remidosol.llmmcp.job.application;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test — no Spring context. The estimate is deliberately simplistic (PRD §4.4):
 * {@code max(1, ceil(len/4) * multiplier)}, multiplier chosen by the model's provider prefix.
 */
class CreditEstimatorTest {

    private final CreditEstimator estimator = new CreditEstimator(
            new CreditProperties(1, Map.of("fake", 1, "openai", 3, "gemini", 2)));

    @Test
    void rounds_the_length_up_and_applies_the_provider_multiplier() {
        // len 5 -> ceil(5/4) = 2; fake multiplier 1
        assertThat(estimator.estimate("12345", "fake:demo")).isEqualTo(2);
        // len 8 -> 2; openai multiplier 3
        assertThat(estimator.estimate("12345678", "openai:gpt-4o-mini")).isEqualTo(6);
        // len 4 -> 1; gemini multiplier 2
        assertThat(estimator.estimate("1234", "gemini:flash")).isEqualTo(2);
    }

    @Test
    void never_estimates_below_one_credit() {
        assertThat(estimator.estimate("", "fake:demo")).isEqualTo(1);
    }

    @Test
    void unknown_provider_prefix_falls_back_to_the_default_multiplier() {
        assertThat(estimator.estimate("12345678", "mystery:model")).isEqualTo(2); // 2 * default(1)
    }
}
