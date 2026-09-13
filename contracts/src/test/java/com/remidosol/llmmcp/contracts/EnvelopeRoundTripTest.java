package com.remidosol.llmmcp.contracts;

import com.remidosol.llmmcp.contracts.event.CreditCaptured;
import com.remidosol.llmmcp.contracts.event.CreditRejected;
import com.remidosol.llmmcp.contracts.event.CreditReleased;
import com.remidosol.llmmcp.contracts.event.CreditReserved;
import com.remidosol.llmmcp.contracts.event.JobCompleted;
import com.remidosol.llmmcp.contracts.event.JobCreated;
import com.remidosol.llmmcp.contracts.event.JobFailed;
import com.remidosol.llmmcp.contracts.event.JobRejected;
import com.remidosol.llmmcp.contracts.event.JobTimedOut;
import com.remidosol.llmmcp.contracts.event.LlmFailed;
import com.remidosol.llmmcp.contracts.event.LlmStarted;
import com.remidosol.llmmcp.contracts.event.LlmSucceeded;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every payload record, wrapped in an envelope, must serialize to a document that validates
 * against the shipped schemas and read back as an equal object (PRD §4.3).
 */
class EnvelopeRoundTripTest {

    private static final UUID JOB = UUID.fromString("6e2c5f6b-3a52-7b1e-8f2d-9c4e5a6b7c8d");
    private static final UUID RES = UUID.fromString("0192f3a4-5b6c-7d8e-9f01-23456789abcd");

    static final Map<String, Object> SAMPLES = Map.ofEntries(
            Map.entry(EventTypes.JOB_CREATED, new JobCreated(JOB, "u1", "hello", "fake:demo", 3)),
            Map.entry(EventTypes.JOB_REJECTED, new JobRejected(JOB, "insufficient credits")),
            Map.entry(EventTypes.JOB_COMPLETED, new JobCompleted(JOB, "u1", 2)),
            Map.entry(EventTypes.JOB_FAILED, new JobFailed(JOB, "u1", "boom")),
            Map.entry(EventTypes.JOB_TIMED_OUT, new JobTimedOut(JOB, "u1")),
            Map.entry(EventTypes.CREDIT_RESERVED, new CreditReserved(JOB, "u1", RES, 3, "hello", "fake:demo")),
            Map.entry(EventTypes.CREDIT_REJECTED, new CreditRejected(JOB, "u2", "insufficient credits", 5, 12)),
            Map.entry(EventTypes.CREDIT_CAPTURED, new CreditCaptured(JOB, RES, 2)),
            Map.entry(EventTypes.CREDIT_RELEASED, new CreditReleased(JOB, RES, 3)),
            Map.entry(EventTypes.LLM_STARTED, new LlmStarted(JOB, "fake", "demo", 1)),
            Map.entry(EventTypes.LLM_SUCCEEDED, new LlmSucceeded(JOB, "fake", "demo", "canned", 4, 8, 2)),
            Map.entry(EventTypes.LLM_FAILED, new LlmFailed(JOB, "fake", "demo", "[FAIL]", false, 1)));

    static Stream<Arguments> samples() {
        return EventTypes.PAYLOAD_TYPES.keySet().stream().sorted()
                .map(type -> Arguments.of(type, SAMPLES.get(type)));
    }

    @Test
    void every_event_type_has_a_sample_here() {
        assertThat(SAMPLES.keySet()).containsExactlyInAnyOrderElementsOf(EventTypes.PAYLOAD_TYPES.keySet());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void record_serializes_to_a_schema_valid_envelope_and_reads_back_equal(String type, Object payload) {
        EventEnvelope envelope = EventEnvelope.create(type, aggregateOf(type), JOB.toString(), JOB.toString(),
                null, producerOf(type), payload);

        String json = envelope.toJson();
        JsonNode tree = ContractsJson.MAPPER.readTree(json);

        assertThat(SchemaSupport.validate(SchemaSupport.envelopeSchema(), tree)).as("envelope schema").isEmpty();
        assertThat(SchemaSupport.validate(SchemaSupport.payloadSchema(type), tree.get("payload")))
                .as("payload schema for " + type).isEmpty();
        assertThat(tree.get("occurredAt").isString()).as("ISO-8601 timestamp, not epoch").isTrue();

        EventEnvelope back = EventEnvelope.fromJson(json);
        assertThat(back).isEqualTo(envelope);
        assertThat(back.payloadAs(EventTypes.PAYLOAD_TYPES.get(type))).isEqualTo(payload);
        assertThat(back.eventId().version()).isEqualTo(7);
    }

    private static String aggregateOf(String type) {
        if (type.startsWith("Job")) return AggregateTypes.JOB;
        if (type.startsWith("Credit")) return AggregateTypes.CREDIT;
        return AggregateTypes.LLM;
    }

    private static String producerOf(String type) {
        if (type.startsWith("Job")) return "job-service";
        if (type.startsWith("Credit")) return "credit-service";
        return "llm-worker";
    }
}
