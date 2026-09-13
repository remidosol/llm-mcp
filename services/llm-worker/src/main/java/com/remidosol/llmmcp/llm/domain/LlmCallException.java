package com.remidosol.llmmcp.llm.domain;

/**
 * A failed provider call, classified: {@code retryable} (429, 5xx, timeouts — try again later)
 * or not (bad request, unknown model, injected [FAIL]). The classification drives the retry policy
 * and the {@code LlmFailed.retryable} flag consumers see.
 */
public class LlmCallException extends RuntimeException {

    private final boolean retryable;
    private final int tries;

    public LlmCallException(String message, boolean retryable) {
        this(message, retryable, null, 1);
    }

    public LlmCallException(String message, boolean retryable, Throwable cause) {
        this(message, retryable, cause, 1);
    }

    private LlmCallException(String message, boolean retryable, Throwable cause, int tries) {
        super(message, cause);
        this.retryable = retryable;
        this.tries = tries;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /** How many provider calls were made before giving up (retries included). */
    public int tries() {
        return tries;
    }

    public LlmCallException withTries(int tries) {
        return new LlmCallException(getMessage(), retryable, getCause(), tries);
    }
}
