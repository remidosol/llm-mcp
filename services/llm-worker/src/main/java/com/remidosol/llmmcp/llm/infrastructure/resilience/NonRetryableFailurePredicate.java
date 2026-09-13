package com.remidosol.llmmcp.llm.infrastructure.resilience;

import com.remidosol.llmmcp.llm.domain.LlmCallException;

import java.util.function.Predicate;

/** The breaker ignores client-side failures (bad request, unknown model): they say nothing about provider health. */
public class NonRetryableFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return throwable instanceof LlmCallException e && !e.isRetryable();
    }
}
