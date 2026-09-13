package com.remidosol.llmmcp.job.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.remidosol.llmmcp.job.domain.JobStatus.COMPLETED;
import static com.remidosol.llmmcp.job.domain.JobStatus.CREATED;
import static com.remidosol.llmmcp.job.domain.JobStatus.CREDIT_RESERVED;
import static com.remidosol.llmmcp.job.domain.JobStatus.FAILED;
import static com.remidosol.llmmcp.job.domain.JobStatus.PROCESSING;
import static com.remidosol.llmmcp.job.domain.JobStatus.REJECTED;
import static com.remidosol.llmmcp.job.domain.JobStatus.TIMED_OUT;
import static org.assertj.core.api.Assertions.assertThat;

/** Out-of-order events imply their predecessors; backwards or terminal moves are rejected. */
class SagaPathTest {

    @Test
    void direct_transitions_are_single_steps() {
        assertThat(SagaPath.impliedPath(CREATED, CREDIT_RESERVED)).contains(List.of(CREDIT_RESERVED));
        assertThat(SagaPath.impliedPath(CREATED, REJECTED)).contains(List.of(REJECTED));
        assertThat(SagaPath.impliedPath(PROCESSING, COMPLETED)).contains(List.of(COMPLETED));
    }

    @Test
    void a_success_that_overtakes_start_and_reservation_walks_the_chain() {
        assertThat(SagaPath.impliedPath(CREATED, COMPLETED)).contains(List.of(CREDIT_RESERVED, PROCESSING, COMPLETED));
        assertThat(SagaPath.impliedPath(CREDIT_RESERVED, COMPLETED)).contains(List.of(PROCESSING, COMPLETED));
        assertThat(SagaPath.impliedPath(CREATED, FAILED)).contains(List.of(CREDIT_RESERVED, FAILED));
    }

    @Test
    void backwards_terminal_and_self_transitions_are_rejected() {
        assertThat(SagaPath.impliedPath(PROCESSING, CREDIT_RESERVED)).isEmpty();
        assertThat(SagaPath.impliedPath(COMPLETED, PROCESSING)).isEmpty();
        assertThat(SagaPath.impliedPath(TIMED_OUT, COMPLETED)).isEmpty();
        assertThat(SagaPath.impliedPath(PROCESSING, PROCESSING)).isEmpty();
        assertThat(SagaPath.impliedPath(CREATED, TIMED_OUT)).contains(List.of(CREDIT_RESERVED, TIMED_OUT));
    }
}
