package com.remidosol.llmmcp.llm;

import com.remidosol.llmmcp.contracts.AggregateTypes;
import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventHeaders;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.Topics;
import com.remidosol.llmmcp.contracts.event.CreditReserved;
import com.remidosol.llmmcp.contracts.event.LlmFailed;
import com.remidosol.llmmcp.contracts.event.LlmStarted;
import com.remidosol.llmmcp.contracts.event.LlmSucceeded;
import com.remidosol.llmmcp.llm.application.port.LlmAttemptRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** CreditReserved in → LlmStarted + LlmSucceeded/LlmFailed out, with the attempt trail and the inbox. */
class LlmExecutionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private LlmAttemptRepository attempts;

    private Consumer<String, String> llmEvents;

    @BeforeEach
    void subscribe() {
        llmEvents = consumerFactory.createConsumer("test-" + UUID.randomUUID(), "");
        llmEvents.subscribe(List.of(Topics.LLM_EVENTS));
    }

    @AfterEach
    void close() {
        llmEvents.close();
    }

    @Test
    void reserved_job_is_executed_by_the_fake_provider_and_priced() {
        UUID jobId = UUID.randomUUID();
        String prompt = "Explain the outbox pattern in two sentences.";
        EventEnvelope trigger = reserved(jobId, prompt, "fake:demo");

        kafka.send(record(trigger));

        EventEnvelope started = EventEnvelope.fromJson(awaitRecord(jobId, EventTypes.LLM_STARTED).value());
        assertThat(started.causationId()).isEqualTo(trigger.eventId());
        assertThat(started.payloadAs(LlmStarted.class).attemptNo()).isEqualTo(1);

        LlmSucceeded succeeded = EventEnvelope.fromJson(awaitRecord(jobId, EventTypes.LLM_SUCCEEDED).value())
                .payloadAs(LlmSucceeded.class);
        assertThat(succeeded.provider()).isEqualTo("fake");
        assertThat(succeeded.model()).isEqualTo("demo");
        assertThat(succeeded.output()).startsWith("FAKE(demo): Explain the outbox");
        assertThat(succeeded.actualCredits()).isGreaterThanOrEqualTo(1);
        assertThat(succeeded.promptTokens()).isEqualTo((int) Math.ceil(prompt.length() / 4.0)); // fake tokenizer
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(attempts.countByJobId(jobId)).isEqualTo(1));
    }

    @Test
    void FAIL_prompt_yields_a_non_retryable_LlmFailed() {
        UUID jobId = UUID.randomUUID();
        kafka.send(record(reserved(jobId, "[FAIL] please break", "fake:demo")));

        LlmFailed failed = EventEnvelope.fromJson(awaitRecord(jobId, EventTypes.LLM_FAILED).value()).payloadAs(LlmFailed.class);
        assertThat(failed.retryable()).isFalse();
        assertThat(failed.attempts()).isEqualTo(1);
        assertThat(failed.reason()).contains("[FAIL]");
    }

    @Test
    void FLAKY_prompt_is_retried_in_process_and_succeeds_within_one_delivery() {
        UUID jobId = UUID.randomUUID();
        kafka.send(record(reserved(jobId, "[FLAKY] fails twice then works", "fake:demo")));

        LlmStarted started = EventEnvelope.fromJson(awaitRecord(jobId, EventTypes.LLM_STARTED).value()).payloadAs(LlmStarted.class);
        assertThat(started.attemptNo()).as("one delivery, one attempt row").isEqualTo(1);
        awaitRecord(jobId, EventTypes.LLM_SUCCEEDED); // tries 1 and 2 failed inside Resilience4j, try 3 succeeded
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(attempts.countByJobId(jobId)).isEqualTo(1));
    }

    @Test
    void unknown_provider_prefix_fails_fast_without_an_attempt() {
        UUID jobId = UUID.randomUUID();
        kafka.send(record(reserved(jobId, "hello", "nope:model")));

        LlmFailed failed = EventEnvelope.fromJson(awaitRecord(jobId, EventTypes.LLM_FAILED).value()).payloadAs(LlmFailed.class);
        assertThat(failed.retryable()).isFalse();
        assertThat(failed.reason()).contains("no provider configured for 'nope'");
        assertThat(attempts.countByJobId(jobId)).isZero();
    }

    @Test
    void duplicate_CreditReserved_runs_the_provider_exactly_once() {
        UUID jobId = UUID.randomUUID();
        EventEnvelope trigger = reserved(jobId, "once please", "fake:demo");

        kafka.send(record(trigger));
        kafka.send(record(trigger));

        awaitRecord(jobId, EventTypes.LLM_SUCCEEDED);
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).until(() -> attempts.countByJobId(jobId) == 1);
    }

    private static EventEnvelope reserved(UUID jobId, String prompt, String model) {
        return EventEnvelope.create(EventTypes.CREDIT_RESERVED, AggregateTypes.CREDIT, jobId.toString(), jobId.toString(),
                null, "credit-service", new CreditReserved(jobId, "u1", UUID.randomUUID(), 12, prompt, model));
    }

    private static ProducerRecord<String, String> record(EventEnvelope envelope) {
        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.CREDIT_EVENTS, envelope.aggregateId(), envelope.toJson());
        record.headers().add(EventHeaders.EVENT_TYPE, envelope.eventType().getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private ConsumerRecord<String, String> awaitRecord(UUID jobId, String eventType) {
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> candidate : KafkaTestUtils.getRecords(llmEvents, Duration.ofMillis(500))) {
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
