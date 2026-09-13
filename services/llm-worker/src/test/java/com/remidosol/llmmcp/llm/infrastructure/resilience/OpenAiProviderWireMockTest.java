package com.remidosol.llmmcp.llm.infrastructure.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.remidosol.llmmcp.llm.TestcontainersConfiguration;
import com.remidosol.llmmcp.llm.application.LlmProviders;
import com.remidosol.llmmcp.llm.application.port.ProviderGateway;
import com.remidosol.llmmcp.llm.domain.LlmCallException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The real OpenAI adapter (openai-java SDK through Spring AI) against a WireMock upstream: success
 * parsing, retry on 429, and the breaker opening after a burst of 500s.
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "resilience4j.retry.configs.default.wait-duration=20ms",
        "resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=60s"
})
@Import(TestcontainersConfiguration.class)
class OpenAiProviderWireMockTest {

    static final WireMockServer OPENAI = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final String COMPLETION = """
            {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"gpt-4o-mini",
             "choices":[{"index":0,"message":{"role":"assistant","content":"hello from wiremock"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":5,"completion_tokens":3,"total_tokens":8}}
            """;

    @BeforeAll
    static void start() {
        OPENAI.start();
    }

    @AfterAll
    static void stop() {
        OPENAI.stop();
    }

    @DynamicPropertySource
    static void upstream(DynamicPropertyRegistry registry) {
        if (!OPENAI.isRunning()) {
            OPENAI.start();
        }
        registry.add("spring.ai.openai.base-url", () -> "http://localhost:" + OPENAI.port() + "/v1");
    }

    @Autowired
    private LlmProviders providers;

    @Autowired
    private ProviderGateway gateway;

    @Autowired
    private CircuitBreakerRegistry breakers;

    @BeforeEach
    void reset() {
        OPENAI.resetAll();
        breakers.circuitBreaker("openai").reset();
    }

    @Test
    void parses_output_and_usage_from_a_chat_completion() {
        OPENAI.stubFor(post(urlPathEqualTo("/v1/chat/completions")).willReturn(okJson(COMPLETION)));

        ProviderGateway.Outcome outcome = gateway.call(providers.forModel(LlmProviders.ModelRef.parse("openai:gpt-4o-mini")),
                "gpt-4o-mini", "hi", 64);

        assertThat(outcome.result().output()).isEqualTo("hello from wiremock");
        assertThat(outcome.result().promptTokens()).isEqualTo(5);
        assertThat(outcome.result().completionTokens()).isEqualTo(3);
        assertThat(outcome.tries()).isEqualTo(1);
    }

    @Test
    void retries_429_and_succeeds_on_the_third_try() {
        OPENAI.stubFor(post(urlPathEqualTo("/v1/chat/completions")).inScenario("flaky")
                .whenScenarioStateIs(STARTED).willReturn(aResponse().withStatus(429).withBody("{\"error\":{\"message\":\"slow down\"}}"))
                .willSetStateTo("second"));
        OPENAI.stubFor(post(urlPathEqualTo("/v1/chat/completions")).inScenario("flaky")
                .whenScenarioStateIs("second").willReturn(aResponse().withStatus(429).withBody("{\"error\":{\"message\":\"slow down\"}}"))
                .willSetStateTo("third"));
        OPENAI.stubFor(post(urlPathEqualTo("/v1/chat/completions")).inScenario("flaky")
                .whenScenarioStateIs("third").willReturn(okJson(COMPLETION)));

        ProviderGateway.Outcome outcome = gateway.call(providers.forModel(LlmProviders.ModelRef.parse("openai:gpt-4o-mini")),
                "gpt-4o-mini", "hi", 64);

        assertThat(outcome.tries()).isEqualTo(3);
        assertThat(outcome.result().output()).isEqualTo("hello from wiremock");
    }

    @Test
    void a_burst_of_500s_opens_the_breaker_and_later_calls_fail_fast() {
        OPENAI.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":{\"message\":\"boom\"}}")));
        var provider = providers.forModel(LlmProviders.ModelRef.parse("openai:gpt-4o-mini"));

        // 4 deliveries × 3 tries = 12 failed calls > window of 10 at 100 % failure rate
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> gateway.call(provider, "gpt-4o-mini", "hi", 64)).isInstanceOf(LlmCallException.class);
        }

        assertThat(breakers.circuitBreaker("openai").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int requestsBefore = OPENAI.getAllServeEvents().size();
        assertThatThrownBy(() -> gateway.call(provider, "gpt-4o-mini", "hi", 64))
                .isInstanceOf(LlmCallException.class).hasMessageContaining("circuit breaker open");
        assertThat(OPENAI.getAllServeEvents().size()).as("no upstream call while open").isEqualTo(requestsBefore);
    }
}
