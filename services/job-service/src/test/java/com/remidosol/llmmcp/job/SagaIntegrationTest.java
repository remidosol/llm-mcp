package com.remidosol.llmmcp.job;

import com.remidosol.llmmcp.contracts.AggregateTypes;
import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventHeaders;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.Topics;
import com.remidosol.llmmcp.contracts.event.CreditRejected;
import com.remidosol.llmmcp.contracts.event.CreditReserved;
import com.remidosol.llmmcp.contracts.event.JobCompleted;
import com.remidosol.llmmcp.contracts.event.LlmFailed;
import com.remidosol.llmmcp.contracts.event.LlmStarted;
import com.remidosol.llmmcp.contracts.event.LlmSucceeded;
import com.remidosol.llmmcp.job.application.port.JobResultRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The choreography from job-service's seat: events in, status transitions + outbox events out.
 * Covers the happy path, rejection, failure, out-of-order delivery, the watchdog and late results.
 */
class SagaIntegrationTest extends AbstractIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private JobResultRepository results;

    @Autowired
    private MeterRegistry metrics;

    private Consumer<String, String> jobEvents;

    @BeforeEach
    void subscribe() {
        jobEvents = consumerFactory.createConsumer("test-" + UUID.randomUUID(), "");
        jobEvents.subscribe(List.of(Topics.JOB_EVENTS));
    }

    @AfterEach
    void close() {
        jobEvents.close();
    }

    @Test
    void happy_path_reserved_started_succeeded_completes_the_job_and_emits_JobCompleted() {
        UUID jobId = createJob("saga-user");

        send(Topics.CREDIT_EVENTS, creditReserved(jobId, "saga-user"));
        awaitStatus(jobId, "CREDIT_RESERVED");
        send(Topics.LLM_EVENTS, llm(EventTypes.LLM_STARTED, jobId, new LlmStarted(jobId, "fake", "demo", 1)));
        awaitStatus(jobId, "PROCESSING");
        EventEnvelope succeeded = llm(EventTypes.LLM_SUCCEEDED, jobId,
                new LlmSucceeded(jobId, "fake", "demo", "canned output", 4, 8, 7));
        send(Topics.LLM_EVENTS, succeeded);
        awaitStatus(jobId, "COMPLETED");

        Map<String, Object> job = getJob(jobId);
        assertThat(job).containsEntry("actualCredits", 7);
        Map<String, Object> result = rest.exchange("/api/jobs/" + jobId + "/result", HttpMethod.GET, HttpEntity.EMPTY, MAP).getBody();
        assertThat(result).containsEntry("output", "canned output").containsEntry("late", false);

        EventEnvelope completed = EventEnvelope.fromJson(awaitRecord(jobId, EventTypes.JOB_COMPLETED).value());
        assertThat(completed.causationId()).isEqualTo(succeeded.eventId());
        assertThat(completed.payloadAs(JobCompleted.class).actualCredits()).isEqualTo(7);
    }

    @Test
    void CreditRejected_ends_the_saga_with_REJECTED_and_JobRejected() {
        UUID jobId = createJob("poor-user");

        send(Topics.CREDIT_EVENTS, credit(EventTypes.CREDIT_REJECTED, jobId,
                new CreditRejected(jobId, "poor-user", "insufficient credits", 5, 12)));

        awaitStatus(jobId, "REJECTED");
        assertThat(getJob(jobId)).containsEntry("failureReason", "insufficient credits");
        awaitRecord(jobId, EventTypes.JOB_REJECTED);
        assertThat(rest.exchange("/api/jobs/" + jobId + "/result", HttpMethod.GET, HttpEntity.EMPTY, MAP)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void LlmFailed_compensates_with_FAILED_and_JobFailed() {
        UUID jobId = createJob("fail-user");
        send(Topics.CREDIT_EVENTS, creditReserved(jobId, "fail-user"));
        awaitStatus(jobId, "CREDIT_RESERVED");

        send(Topics.LLM_EVENTS, llm(EventTypes.LLM_FAILED, jobId, new LlmFailed(jobId, "fake", "demo", "[FAIL]", false, 1)));

        awaitStatus(jobId, "FAILED");
        awaitRecord(jobId, EventTypes.JOB_FAILED);
    }

    @Test
    void out_of_order_LlmSucceeded_before_LlmStarted_still_completes_and_the_stale_start_is_ignored() {
        UUID jobId = createJob("ooo-user");
        double rejectedBefore = guardRejected("COMPLETED", "PROCESSING");

        send(Topics.LLM_EVENTS, llm(EventTypes.LLM_SUCCEEDED, jobId, new LlmSucceeded(jobId, "fake", "demo", "early", 1, 1, 1)));
        awaitStatus(jobId, "COMPLETED"); // implied path CREATED -> CREDIT_RESERVED -> PROCESSING -> COMPLETED

        send(Topics.LLM_EVENTS, llm(EventTypes.LLM_STARTED, jobId, new LlmStarted(jobId, "fake", "demo", 1)));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(guardRejected("COMPLETED", "PROCESSING")).isGreaterThan(rejectedBefore));
        assertThat(getJob(jobId)).containsEntry("status", "COMPLETED");
    }

    @Test
    void watchdog_times_out_a_stuck_job_and_a_late_result_is_kept_but_does_not_reopen_it() {
        UUID jobId = createJob("slow-user");
        send(Topics.CREDIT_EVENTS, creditReserved(jobId, "slow-user"));
        send(Topics.LLM_EVENTS, llm(EventTypes.LLM_STARTED, jobId, new LlmStarted(jobId, "fake", "demo", 1)));
        awaitStatus(jobId, "PROCESSING");
        double lateBefore = metrics.counter("saga.late_result").count();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(status(jobId)).isEqualTo("TIMED_OUT"));
        awaitRecord(jobId, EventTypes.JOB_TIMED_OUT);

        send(Topics.LLM_EVENTS, llm(EventTypes.LLM_SUCCEEDED, jobId, new LlmSucceeded(jobId, "fake", "demo", "too late", 1, 1, 1)));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(metrics.counter("saga.late_result").count()).isGreaterThan(lateBefore));
        assertThat(status(jobId)).isEqualTo("TIMED_OUT");
        assertThat(results.findById(jobId)).get().satisfies(r -> assertThat(r.isLate()).isTrue());
        assertThat(rest.exchange("/api/jobs/" + jobId + "/result", HttpMethod.GET, HttpEntity.EMPTY, MAP)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- helpers ----

    private UUID createJob(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId);
        Map<String, Object> body = rest.exchange("/api/jobs", HttpMethod.POST,
                new HttpEntity<>("{\"prompt\": \"saga\", \"model\": \"fake:demo\"}", headers), MAP).getBody();
        return UUID.fromString((String) body.get("jobId"));
    }

    private Map<String, Object> getJob(UUID jobId) {
        return rest.exchange("/api/jobs/" + jobId, HttpMethod.GET, HttpEntity.EMPTY, MAP).getBody();
    }

    private String status(UUID jobId) {
        return (String) getJob(jobId).get("status");
    }

    private void awaitStatus(UUID jobId, String expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(status(jobId)).isEqualTo(expected));
    }

    private double guardRejected(String from, String to) {
        return metrics.counter("saga.guard_rejected", "from", from, "to", to).count();
    }

    private static EventEnvelope creditReserved(UUID jobId, String userId) {
        return credit(EventTypes.CREDIT_RESERVED, jobId, new CreditReserved(jobId, userId, UUID.randomUUID(), 12, "saga", "fake:demo"));
    }

    private static EventEnvelope credit(String type, UUID jobId, Object payload) {
        return EventEnvelope.create(type, AggregateTypes.CREDIT, jobId.toString(), jobId.toString(), null, "credit-service", payload);
    }

    private static EventEnvelope llm(String type, UUID jobId, Object payload) {
        return EventEnvelope.create(type, AggregateTypes.LLM, jobId.toString(), jobId.toString(), null, "llm-worker", payload);
    }

    private void send(String topic, EventEnvelope envelope) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, envelope.aggregateId(), envelope.toJson());
        record.headers().add(EventHeaders.EVENT_TYPE, envelope.eventType().getBytes(StandardCharsets.UTF_8));
        kafka.send(record);
    }

    private ConsumerRecord<String, String> awaitRecord(UUID jobId, String eventType) {
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> candidate : KafkaTestUtils.getRecords(jobEvents, Duration.ofMillis(500))) {
                var header = candidate.headers().lastHeader(EventHeaders.EVENT_TYPE);
                if (jobId.toString().equals(candidate.key()) && header != null
                        && eventType.equals(new String(header.value(), StandardCharsets.UTF_8))) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("no " + eventType + " for job " + jobId + " within 30s");
    }
}
