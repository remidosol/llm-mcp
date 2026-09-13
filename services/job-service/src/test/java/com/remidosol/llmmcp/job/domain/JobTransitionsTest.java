package com.remidosol.llmmcp.job.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The full transition matrix: 7×7 = 49 pairs, of which exactly 8 are legal. Owner:
 * [USER] (task 1.5) — Claude provides the parameterized skeleton, you provide the truth.
 */
class JobTransitionsTest {
    record Pair(JobStatus from, JobStatus to) {
    }

    static final Set<Pair> VALID = Set.of(
        new Pair(JobStatus.CREATED, JobStatus.CREDIT_RESERVED),
        new Pair(JobStatus.CREATED, JobStatus.REJECTED),
        new Pair(JobStatus.CREDIT_RESERVED, JobStatus.PROCESSING),
        new Pair(JobStatus.CREDIT_RESERVED, JobStatus.FAILED),
        new Pair(JobStatus.CREDIT_RESERVED, JobStatus.TIMED_OUT),
        new Pair(JobStatus.PROCESSING, JobStatus.COMPLETED),
        new Pair(JobStatus.PROCESSING, JobStatus.FAILED),
        new Pair(JobStatus.PROCESSING, JobStatus.TIMED_OUT)
    );

    static Stream<Arguments> validPairs() {
        return VALID.stream().map(p -> Arguments.of(p.from(), p.to()));
    }

    /**
     * All 49 combinations — the invalid set is everything not in {@link #validPairs()}.
     */
    static Stream<Arguments> allPairs() {
        return Arrays.stream(JobStatus.values())
            .flatMap(from -> Arrays.stream(JobStatus.values())
                .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @MethodSource("validPairs")
    void every_pair_from_the_prd_diagram_is_allowed(JobStatus from, JobStatus to) {
        assertThat(JobTransitions.isAllowed(from, to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void the_matrix_matches_the_table_exactly(JobStatus from, JobStatus to) {
        assertThat(JobTransitions.isAllowed(from, to)).isEqualTo(VALID.contains(new Pair(from, to)));
    }

    @ParameterizedTest(name = "{0} has no outgoing transitions")
    @EnumSource(value = JobStatus.class, names = {"COMPLETED", "FAILED", "REJECTED", "TIMED_OUT"})
    void terminal_states_have_no_outgoing_transitions(JobStatus terminal) {
        for (JobStatus to : JobStatus.values()) {
            assertThat(JobTransitions.isAllowed(terminal, to)).as("%s -> %s", terminal, to).isFalse();
        }
    }

    @Test
    void transitionTo_on_an_invalid_pair_throws_and_leaves_the_job_untouched() {
        Job job = Job.create(UUID.randomUUID(), "u1", "p", "fake:demo", 1);

        assertThatThrownBy(() -> job.transitionTo(JobStatus.COMPLETED))
            .isInstanceOf(InvalidTransitionException.class);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CREATED);
    }
}
