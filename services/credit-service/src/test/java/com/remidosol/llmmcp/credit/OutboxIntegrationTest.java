package com.remidosol.llmmcp.credit;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.credit.application.TopUpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.remidosol.llmmcp.credit.KafkaTestSupport.jobCreated;
import static com.remidosol.llmmcp.credit.KafkaTestSupport.jobCreatedRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Task 3.4: the reservation and its CreditReserved envelope commit in one transaction. */
class OutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private TopUpService topUp;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void reservation_writes_CreditReserved_to_the_outbox_and_the_poller_publishes_it() {
        String user = "outbox-" + UUID.randomUUID();
        topUp.topUp(user, 50);
        UUID jobId = UUID.randomUUID();
        EventEnvelope trigger = jobCreated(jobId, user, 7);

        kafka.send(jobCreatedRecord(trigger));

        // ignoreExceptions: the row does not exist until the listener commits, and an empty result is an
        // exception (EmptyResultDataAccessException), not an AssertionError, which Awaitility would otherwise rethrow
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() -> {
            Map<String, Object> row = jdbc.sql(
                            "select type, payload::text as payload, published_at from outbox where aggregateid = :id")
                    .param("id", jobId.toString()).query().singleRow();
            assertThat(row).containsEntry("type", EventTypes.CREDIT_RESERVED);
            assertThat(EventEnvelope.fromJson((String) row.get("payload")).causationId()).isEqualTo(trigger.eventId());
            assertThat(row.get("published_at")).as("poller marked it published").isNotNull();
        });
    }
}
