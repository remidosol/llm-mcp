package com.remidosol.llmmcp.llm.infrastructure.messaging;

/**
 * Marks a failure that retrying cannot fix (unparseable record, schema violation). The error
 * handler sends such records straight to the dead-letter topic instead of burning the backoff
 * budget (ADR-0008).
 */
public class NonRetryableException extends RuntimeException {

    public NonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
