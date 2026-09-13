package com.remidosol.llmmcp.job;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventHeaders;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.Topics;
import com.remidosol.llmmcp.contracts.event.JobCreated;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POST /api/jobs must end with a schema-shaped JobCreated envelope on job.events.v1, keyed by the
 * job id, with the eventType/eventId headers tooling relies on.
 */
class JobEventPublishingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Test
    void creating_a_job_publishes_JobCreated_keyed_by_jobId() {
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("test-" + UUID.randomUUID(), "")) {
            consumer.subscribe(List.of(Topics.JOB_EVENTS));

            String jobId = createJob("kafka-user", "publish me please", "fake:demo");
            ConsumerRecord<String, String> record = awaitRecordWithKey(consumer, jobId);

            assertThat(header(record, EventHeaders.EVENT_TYPE)).isEqualTo(EventTypes.JOB_CREATED);
            EventEnvelope envelope = EventEnvelope.fromJson(record.value());
            assertThat(header(record, EventHeaders.EVENT_ID)).isEqualTo(envelope.eventId().toString());
            assertThat(envelope.eventType()).isEqualTo(EventTypes.JOB_CREATED);
            assertThat(envelope.aggregateType()).isEqualTo("job");
            assertThat(envelope.aggregateId()).isEqualTo(jobId);
            assertThat(envelope.correlationId()).isEqualTo(jobId);
            assertThat(envelope.causationId()).isNull();
            assertThat(envelope.producer()).isEqualTo("job-service");

            JobCreated payload = envelope.payloadAs(JobCreated.class);
            assertThat(payload.jobId()).hasToString(jobId);
            assertThat(payload.userId()).isEqualTo("kafka-user");
            assertThat(payload.prompt()).isEqualTo("publish me please");
            assertThat(payload.model()).isEqualTo("fake:demo");
            assertThat(payload.estimatedCredits()).isEqualTo(5); // ceil(17/4) * fake multiplier 1
        }
    }

    static ConsumerRecord<String, String> awaitRecordWithKey(Consumer<String, String> consumer, String key) {
        Instant deadline = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> candidate : KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500))) {
                if (key.equals(candidate.key())) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("no record with key " + key + " within 20s");
    }

    static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private String createJob(String userId, String prompt, String model) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId);
        Map<String, Object> body = rest.exchange("/api/jobs", HttpMethod.POST,
                new HttpEntity<>("{\"prompt\": \"" + prompt + "\", \"model\": \"" + model + "\"}", headers),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("jobId");
    }
}
