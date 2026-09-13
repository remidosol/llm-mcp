package com.remidosol.llmmcp.llm.infrastructure.resilience;

import com.remidosol.llmmcp.llm.domain.LlmCallException;

import java.util.function.Predicate;

/** Retry only what can heal: provider failures flagged retryable (429, 5xx, timeouts, I/O). */
public class RetryableFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return throwable instanceof LlmCallException e && e.isRetryable();
    }
}
