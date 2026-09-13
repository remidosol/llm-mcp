package com.remidosol.llmmcp.job.domain;

import java.util.Map;
import java.util.Set;

/**
 * The job state machine as a TABLE, not scattered {@code if}s (State pattern, PRD §4.2). It exists
 * because in Phase 4 events for the same job arrive across three Kafka topics and only per-partition
 * order is guaranteed — every saga handler asks this table first, and invalid transitions are
 * ignored, not retried. Keeping the table in one place makes "which transitions are legal" a
 * 10-second code review instead of a hunt.
 *
 * <p>Owner: [USER] (task 1.3). The valid pairs, straight from the PRD §4.2 diagram (8 total):
 * <pre>
 * CREATED         -> CREDIT_RESERVED | REJECTED
 * CREDIT_RESERVED -> PROCESSING | FAILED | TIMED_OUT
 * PROCESSING      -> COMPLETED | FAILED | TIMED_OUT
 * (terminal states have no outgoing transitions; self-transitions are invalid)
 * </pre>
 */
public final class JobTransitions {
    private static final Map<JobStatus, Set<JobStatus>> ALLOWED = Map.of(
        JobStatus.CREATED, Set.of(JobStatus.CREDIT_RESERVED, JobStatus.REJECTED),
        JobStatus.CREDIT_RESERVED, Set.of(JobStatus.PROCESSING, JobStatus.FAILED, JobStatus.TIMED_OUT),
        JobStatus.PROCESSING, Set.of(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.TIMED_OUT)
    );

    private JobTransitions() {
    }

    /** True when {@code from -> to} appears in the table. Never throws. */
    public static boolean isAllowed(JobStatus from, JobStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** Same check, but throws {@link InvalidTransitionException} on an illegal pair. */
    public static void assertAllowed(JobStatus from, JobStatus to) {
        if (!isAllowed(from, to)) {
            throw new InvalidTransitionException(from, to);
        }
    }
}
