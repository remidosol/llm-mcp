package com.remidosol.llmmcp.llm.infrastructure.provider;

import com.remidosol.llmmcp.llm.application.LlmProperties;
import com.remidosol.llmmcp.llm.application.port.LlmProvider;
import com.remidosol.llmmcp.llm.domain.LlmCallException;
import com.remidosol.llmmcp.llm.domain.LlmResult;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The provider that never costs money — default in local and test (PRD §4.5). With failure
 * injection on, prompt prefixes drive chaos: {@code [FAIL]} → non-retryable failure,
 * {@code [FLAKY]} → fails the first two attempts, {@code [SLOW]} → sleeps long enough to trip
 * the job-service watchdog (PRD §4.8).
 */
@Component
public class FakeProvider implements LlmProvider {

    public static final String NAME = "fake";

    private final LlmProperties properties;

    public FakeProvider(LlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public LlmResult complete(String model, String prompt, int maxTokens, int attemptNo) {
        LlmProperties.Fake fake = properties.fake();
        if (fake.failureInjection()) {
            if (prompt.startsWith("[FAIL]")) {
                throw new LlmCallException("injected failure ([FAIL] prompt)", false);
            }
            if (prompt.startsWith("[FLAKY]") && attemptNo <= 2) {
                throw new LlmCallException("injected transient failure ([FLAKY] prompt, attempt " + attemptNo + ")", true);
            }
            if (prompt.startsWith("[SLOW]")) {
                sleep(fake.slowDelay());
            }
        }
        sleep(fake.latency());
        String output = "FAKE(" + model + "): " + summarize(prompt);
        return new LlmResult(output, tokens(prompt), tokens(output));
    }

    private static int tokens(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private static String summarize(String prompt) {
        String trimmed = prompt.strip();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 57) + "...";
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmCallException("interrupted", true, e);
        }
    }
}
