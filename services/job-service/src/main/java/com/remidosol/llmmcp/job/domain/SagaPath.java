package com.remidosol.llmmcp.job.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a saga event into the transition steps it implies. Events for one job arrive across
 * three topics and Kafka orders only within a partition (ADR-0006), so {@code LlmSucceeded} can
 * overtake {@code LlmStarted} or even {@code CreditReserved}. Because every LLM event was CAUSED
 * by a reservation, and every success by a start, the later event implies the earlier states:
 * this class walks the forward chain CREATED → CREDIT_RESERVED → PROCESSING until the table
 * allows the target. Anything else (backwards, from a terminal state, self-transition) is a guard
 * rejection — ignored, never retried.
 */
public final class SagaPath {

    private static final List<JobStatus> CHAIN = List.of(JobStatus.CREATED, JobStatus.CREDIT_RESERVED, JobStatus.PROCESSING);

    private SagaPath() {
    }

    /** The ordered states to pass through (ending with {@code to}), or empty when rejected. */
    public static Optional<List<JobStatus>> impliedPath(JobStatus from, JobStatus to) {
        if (from == to) {
            return Optional.empty();
        }
        List<JobStatus> steps = new ArrayList<>();
        JobStatus current = from;
        while (!JobTransitions.isAllowed(current, to)) {
            int index = CHAIN.indexOf(current);
            if (index < 0 || index == CHAIN.size() - 1) {
                return Optional.empty();
            }
            current = CHAIN.get(index + 1);
            steps.add(current);
        }
        steps.add(to);
        return Optional.of(List.copyOf(steps));
    }
}
