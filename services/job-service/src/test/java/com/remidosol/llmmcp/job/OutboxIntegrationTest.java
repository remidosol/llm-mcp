package com.remidosol.llmmcp.job;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.Topics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.ConsumerFactory;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The transactional outbox contract (PRD Phase 3 DoD): the event row is committed WITH the job,
 * the poller moves it to Kafka, and a Kafka outage loses nothing — rows wait, then drain.
 */
class OutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Test
    void creating_a_job_writes_a_JobCreated_envelope_to_the_outbox_and_the_poller_publishes_it() {
        String jobId = createJob("outbox-user");

        Map<String, Object> row = jdbc.sql("select type, aggregatetype, payload::text as payload from outbox where aggregateid = :id")
                .param("id", jobId).query().singleRow();
        assertThat(row).containsEntry("type", EventTypes.JOB_CREATED).containsEntry("aggregatetype", "job");
        EventEnvelope envelope = EventEnvelope.fromJson((String) row.get("payload"));
        assertThat(envelope.aggregateId()).isEqualTo(jobId);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(pending(List.of(jobId))).isZero());
    }

    @Test
    void events_written_during_a_kafka_outage_are_delivered_after_it() {
        var docker = kafkaContainer.getDockerClient();
        String container = kafkaContainer.getContainerId();
        List<String> jobIds = new ArrayList<>();
        createJob("outage-warmup"); // guarantees the topic exists before we measure offsets
        await().atMost(Duration.ofSeconds(15)).until(() -> endOffset() > 0);
        long before = endOffset();

        docker.pauseContainerCmd(container).exec();
        try {
            for (int i = 0; i < 3; i++) {
                jobIds.add(createJob("outage-user")); // the write path never touches Kafka: still 202
            }
            // rows stay pending for as long as the broker is unreachable
            await().during(Duration.ofSeconds(6)).atMost(Duration.ofSeconds(10))
                    .until(() -> pending(jobIds) == 3);
        } finally {
            docker.unpauseContainerCmd(container).exec();
        }

        // drained: every row was acknowledged by the broker (acks=all) before being marked published
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(pending(jobIds)).isZero());
        // and the broker's log grew by at least those three records (duplicates from producer retries are allowed)
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(endOffset()).isGreaterThanOrEqualTo(before + 3));
    }

    /** Sum of end offsets of job.events.v1 — read with a group-less consumer, so no coordinator is involved. */
    private long endOffset() {
        try (Consumer<String, String> probe = consumerFactory.createConsumer("probe-" + UUID.randomUUID(), "")) {
            List<TopicPartition> partitions = probe.partitionsFor(Topics.JOB_EVENTS).stream()
                    .map(p -> new TopicPartition(p.topic(), p.partition())).toList();
            return probe.endOffsets(partitions).values().stream().mapToLong(Long::longValue).sum();
        }
    }

    private long pending(List<String> jobIds) {
        return jdbc.sql("select count(*) from outbox where published_at is null and aggregateid in (:ids)")
                .param("ids", jobIds).query(Long.class).single();
    }

    private String createJob(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId);
        Map<String, Object> body = rest.exchange("/api/jobs", HttpMethod.POST,
                new HttpEntity<>("{\"prompt\": \"outbox\", \"model\": \"fake:demo\"}", headers),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("jobId");
    }
}
