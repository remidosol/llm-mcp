package com.remidosol.llmmcp.job.domain;

/**
 * The saga state of a job, persisted as text in {@code job.status}. This enum exists because the
 * whole choreography (PRD §4.2) hangs off a single authoritative state machine in job-service;
 * every other service keeps only its local state.
 */
public enum JobStatus {
    CREATED,
    CREDIT_RESERVED,
    PROCESSING,
    COMPLETED,
    FAILED,
    REJECTED,
    TIMED_OUT;

    /** Terminal states have no outgoing transitions (see JobTransitions). */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == REJECTED || this == TIMED_OUT;
    }
}
