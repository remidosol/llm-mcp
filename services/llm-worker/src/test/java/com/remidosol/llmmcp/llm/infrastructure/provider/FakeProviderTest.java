package com.remidosol.llmmcp.llm.infrastructure.provider;

import com.remidosol.llmmcp.llm.application.LlmProperties;
import com.remidosol.llmmcp.llm.domain.LlmCallException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeProviderTest {

    private final FakeProvider provider = new FakeProvider(new LlmProperties(512,
            new LlmProperties.Fake(Duration.ZERO, true, Duration.ofMillis(10))));

    @Test
    void returns_canned_output_with_token_counts() {
        var result = provider.complete("demo", "hello world", 512, 1);

        assertThat(result.output()).isEqualTo("FAKE(demo): hello world");
        assertThat(result.promptTokens()).isEqualTo(3);
        assertThat(result.completionTokens()).isEqualTo(6);
    }

    @Test
    void injection_hooks_follow_the_prd_table() {
        assertThatThrownBy(() -> provider.complete("demo", "[FAIL] x", 512, 1))
                .isInstanceOf(LlmCallException.class).satisfies(e -> assertThat(((LlmCallException) e).isRetryable()).isFalse());
        assertThatThrownBy(() -> provider.complete("demo", "[FLAKY] x", 512, 2))
                .isInstanceOf(LlmCallException.class).satisfies(e -> assertThat(((LlmCallException) e).isRetryable()).isTrue());
        assertThat(provider.complete("demo", "[FLAKY] x", 512, 3).output()).contains("[FLAKY] x");
        assertThat(provider.complete("demo", "[SLOW] x", 512, 1).output()).contains("[SLOW] x");
    }

    @Test
    void injection_is_inert_when_disabled() {
        var quiet = new FakeProvider(new LlmProperties(512, new LlmProperties.Fake(Duration.ZERO, false, Duration.ZERO)));

        assertThat(quiet.complete("demo", "[FAIL] x", 512, 1).output()).contains("[FAIL] x");
    }
}
