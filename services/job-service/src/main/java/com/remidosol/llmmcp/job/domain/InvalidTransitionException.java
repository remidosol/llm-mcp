package com.remidosol.llmmcp.job.domain;

/**
 * Thrown when a state change violates the transition table. It exists so that callers can
 * distinguish "business rule said no" from technical failures: saga handlers (Phase 4) catch it,
 * log at WARN, count it, and IGNORE the event — out-of-order delivery across topics is normal,
 * not an error.
 */
public class InvalidTransitionException extends RuntimeException {

    private final JobStatus from;
    private final JobStatus to;

    public InvalidTransitionException(JobStatus from, JobStatus to) {
        super("Invalid job transition: %s -> %s".formatted(from, to));
        this.from = from;
        this.to = to;
    }

    public JobStatus from() {
        return from;
    }

    public JobStatus to() {
        return to;
    }
}
