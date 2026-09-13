package com.remidosol.llmmcp.e2e;

import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.Topics;
import com.remidosol.llmmcp.contracts.event.LlmSucceeded;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * PRD task 4.5 — the saga end to end against real services on compose: happy path, [FAIL]
 * compensation, [SLOW] timeout + late result, and out-of-order delivery. Requires
 * {@code make compose-up && make run-all} (local profile: job timeout 20 s, fake [SLOW] 30 s).
 */
class SagaE2ETest {

    static {
        // cache-aside residual race: a reader can re-cache a row read just before a commit; polling every
        // 500 ms (not 100 ms) keeps the odds of hitting that millisecond window negligible
        org.awaitility.Awaitility.setDefaultPollInterval(Duration.ofMillis(500));
    }

    @Test
    void happy_path_completes_and_captures_the_actual_cost() {
        String USER = Api.freshUser(100);
        JsonNode before = Api.credits(USER);
        UUID jobId = Api.createJob(USER, "Explain the outbox pattern in two sentences.", "fake:demo");

        await().atMost(Duration.ofSeconds(60)).until(() -> Api.status(jobId).equals("COMPLETED"));
        JsonNode job = Api.job(jobId);
        int actual = job.get("actualCredits").asInt();
        assertThat(actual).isGreaterThanOrEqualTo(1);
        assertThat(Api.result(jobId).statusCode()).isEqualTo(200);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode after = Api.credits(USER);
            assertThat(after.get("reserved").asLong()).isEqualTo(before.get("reserved").asLong());
            assertThat(after.get("balance").asLong()).isEqualTo(before.get("balance").asLong() - actual);
        });
        List<String> trail = Trail.types(Trail.of(jobId, Duration.ofSeconds(5)));
        assertThat(trail).containsExactly(EventTypes.JOB_CREATED, EventTypes.CREDIT_RESERVED, EventTypes.LLM_STARTED,
                EventTypes.LLM_SUCCEEDED, EventTypes.JOB_COMPLETED, EventTypes.CREDIT_CAPTURED);
    }

    @Test
    void FAIL_prompt_fails_the_job_and_releases_the_credits() {
        String USER = Api.freshUser(100);
        JsonNode before = Api.credits(USER);
        UUID jobId = Api.createJob(USER, "[FAIL] this must fail", "fake:demo");

        await().atMost(Duration.ofSeconds(60)).until(() -> Api.status(jobId).equals("FAILED"));
        assertThat(Api.result(jobId).statusCode()).isEqualTo(404);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode after = Api.credits(USER);
            assertThat(after.get("balance").asLong()).isEqualTo(before.get("balance").asLong());
            assertThat(after.get("reserved").asLong()).isEqualTo(before.get("reserved").asLong());
        });
        assertThat(Trail.types(Trail.of(jobId, Duration.ofSeconds(5)))).containsExactly(EventTypes.JOB_CREATED,
                EventTypes.CREDIT_RESERVED, EventTypes.LLM_STARTED, EventTypes.LLM_FAILED, EventTypes.JOB_FAILED,
                EventTypes.CREDIT_RELEASED);
    }

    @Test
    void SLOW_prompt_times_out_releases_the_credits_and_the_late_result_is_recorded_without_reopening() {
        String USER = Api.freshUser(100);
        JsonNode before = Api.credits(USER);
        double lateBefore = Api.metric("saga_late_result_total");
        UUID jobId = Api.createJob(USER, "[SLOW] take your time", "fake:demo");

        await().atMost(Duration.ofSeconds(90)).until(() -> Api.status(jobId).equals("TIMED_OUT"));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(Api.credits(USER).get("reserved").asLong()).isEqualTo(before.get("reserved").asLong()));

        // the fake provider finishes later; the result arrives, is kept late=true, and the job stays TIMED_OUT
        await().atMost(Duration.ofSeconds(90)).untilAsserted(() ->
                assertThat(Api.metric("saga_late_result_total")).isGreaterThan(lateBefore));
        assertThat(Api.status(jobId)).isEqualTo("TIMED_OUT");
        assertThat(Api.result(jobId).statusCode()).isEqualTo(404);
        assertThat(Api.credits(USER).get("balance").asLong()).isEqualTo(before.get("balance").asLong());
        assertThat(Trail.types(Trail.of(jobId, Duration.ofSeconds(5)))).contains(EventTypes.JOB_TIMED_OUT,
                EventTypes.CREDIT_RELEASED, EventTypes.LLM_SUCCEEDED);
    }

    @Test
    void out_of_order_LlmSucceeded_before_LlmStarted_completes_once_and_captures_once() {
        String USER = Api.freshUser(100);
        JsonNode before = Api.credits(USER);
        UUID jobId = Api.createJob(USER, "[SLOW] the real worker is slow, the injected result is fast", "fake:demo");

        Trail.inject(Topics.LLM_EVENTS, EventTypes.LLM_SUCCEEDED, jobId,
                new LlmSucceeded(jobId, "fake", "demo", "injected early result", 3, 5, 2));

        await().atMost(Duration.ofSeconds(60)).until(() -> Api.status(jobId).equals("COMPLETED"));
        assertThat(Api.result(jobId).body()).contains("injected early result");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(Api.credits(USER).get("balance").asLong()).isEqualTo(before.get("balance").asLong() - 2));

        // the worker's own LlmStarted/LlmSucceeded arrive later and must be ignored: still one completion
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(Trail.count(Trail.of(jobId, Duration.ofSeconds(5)))).containsEntry(EventTypes.LLM_SUCCEEDED, 2L));
        assertThat(Api.status(jobId)).isEqualTo("COMPLETED");
        assertThat(Trail.count(Trail.of(jobId, Duration.ofSeconds(5)))).containsEntry(EventTypes.JOB_COMPLETED, 1L)
                .containsEntry(EventTypes.CREDIT_CAPTURED, 1L);
    }
}
