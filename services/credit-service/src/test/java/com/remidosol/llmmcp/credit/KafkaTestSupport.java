package com.remidosol.llmmcp.credit;

import com.remidosol.llmmcp.contracts.AggregateTypes;
import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventHeaders;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.Topics;
import com.remidosol.llmmcp.contracts.event.JobCreated;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Helpers to play job-service on the wire and to read what credit-service answers. */
public final class KafkaTestSupport {

    private KafkaTestSupport() {
    }

    public static EventEnvelope jobCreated(UUID jobId, String userId, int estimatedCredits) {
        return EventEnvelope.create(EventTypes.JOB_CREATED, AggregateTypes.JOB, jobId.toString(), jobId.toString(),
                null, "job-service", new JobCreated(jobId, userId, "prompt for " + jobId, "fake:demo", estimatedCredits));
    }

    public static ProducerRecord<String, String> record(String topic, String key, EventEnvelope envelope) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, envelope.toJson());
        record.headers()
                .add(EventHeaders.EVENT_TYPE, envelope.eventType().getBytes(StandardCharsets.UTF_8))
                .add(EventHeaders.EVENT_ID, envelope.eventId().toString().getBytes(StandardCharsets.UTF_8));
        return record;
    }

    public static ProducerRecord<String, String> jobCreatedRecord(EventEnvelope envelope) {
        return record(Topics.JOB_EVENTS, envelope.aggregateId(), envelope);
    }

    public static ConsumerRecord<String, String> awaitRecord(Consumer<String, String> consumer, String key,
                                                             String eventType) {
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> candidate : KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500))) {
                if (key.equals(candidate.key()) && (eventType == null || eventType.equals(header(candidate, EventHeaders.EVENT_TYPE)))) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("no " + eventType + " record with key " + key + " within 30s");
    }

    public static String header(ConsumerRecord<String, String> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
