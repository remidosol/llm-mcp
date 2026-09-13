package com.remidosol.llmmcp.credit;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.Topics;
import com.remidosol.llmmcp.contracts.event.CreditRejected;
import com.remidosol.llmmcp.contracts.event.CreditReserved;
import com.remidosol.llmmcp.credit.application.TopUpService;
import com.remidosol.llmmcp.credit.application.port.CreditAccountRepository;
import com.remidosol.llmmcp.credit.application.port.CreditReservationRepository;
import com.remidosol.llmmcp.credit.domain.ReservationStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.remidosol.llmmcp.credit.KafkaTestSupport.awaitRecord;
import static com.remidosol.llmmcp.credit.KafkaTestSupport.jobCreated;
import static com.remidosol.llmmcp.credit.KafkaTestSupport.jobCreatedRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The saga's second step on the real wire (PRD Phase 2 DoD + task 2.6): JobCreated in, a
 * reservation row and CreditReserved/CreditRejected out; duplicates absorbed by the inbox; poison
 * records parked on the DLT; unknown event types skipped.
 */
class CreditReservationFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private TopUpService topUp;

    @Autowired
    private CreditAccountRepository accounts;

    @Autowired
    private CreditReservationRepository reservations;

    @Autowired
    private MeterRegistry metrics;

    private Consumer<String, String> creditEvents;

    @BeforeEach
    void subscribe() {
        creditEvents = consumerFactory.createConsumer("test-" + UUID.randomUUID(), "");
        creditEvents.subscribe(List.of(Topics.CREDIT_EVENTS));
    }

    @AfterEach
    void close() {
        creditEvents.close();
    }

    @Test
    void JobCreated_reserves_credits_and_publishes_CreditReserved_with_causation() {
        String user = "flow-" + UUID.randomUUID();
        topUp.topUp(user, 100);
        UUID jobId = UUID.randomUUID();
        EventEnvelope trigger = jobCreated(jobId, user, 12);

        kafka.send(jobCreatedRecord(trigger));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(reservations.findByJobId(jobId)).isPresent());
        var reservation = reservations.findByJobId(jobId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservation.getAmount()).isEqualTo(12);
        assertThat(accounts.findByUserId(user).orElseThrow().getReserved()).isEqualTo(12);
        assertThat(accounts.findByUserId(user).orElseThrow().available()).isEqualTo(88);

        ConsumerRecord<String, String> record = awaitRecord(creditEvents, jobId.toString(), EventTypes.CREDIT_RESERVED);
        EventEnvelope envelope = EventEnvelope.fromJson(record.value());
        assertThat(envelope.causationId()).isEqualTo(trigger.eventId());
        assertThat(envelope.correlationId()).isEqualTo(jobId.toString());
        assertThat(envelope.producer()).isEqualTo("credit-service");
        CreditReserved payload = envelope.payloadAs(CreditReserved.class);
        assertThat(payload.reservationId()).isEqualTo(reservation.getId());
        assertThat(payload.amount()).isEqualTo(12);
        assertThat(payload.prompt()).isEqualTo("prompt for " + jobId); // event-carried state (ADR-0010)
        assertThat(payload.model()).isEqualTo("fake:demo");
    }

    @Test
    void duplicate_delivery_of_the_same_event_reserves_exactly_once() {
        String user = "dup-" + UUID.randomUUID();
        topUp.topUp(user, 100);
        UUID jobId = UUID.randomUUID();
        EventEnvelope trigger = jobCreated(jobId, user, 10);
        double duplicatesBefore = metrics.counter("inbox.duplicate").count();

        kafka.send(jobCreatedRecord(trigger));
        kafka.send(jobCreatedRecord(trigger)); // same eventId: at-least-once delivery, simulated

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(metrics.counter("inbox.duplicate").count()).isGreaterThan(duplicatesBefore));
        assertThat(reservations.findByJobId(jobId)).isPresent();
        assertThat(accounts.findByUserId(user).orElseThrow().getReserved()).isEqualTo(10);
    }

    @Test
    void insufficient_credits_publishes_CreditRejected_and_holds_nothing() {
        String user = "poor-" + UUID.randomUUID();
        topUp.topUp(user, 5);
        UUID jobId = UUID.randomUUID();
        EventEnvelope trigger = jobCreated(jobId, user, 12);

        kafka.send(jobCreatedRecord(trigger));

        ConsumerRecord<String, String> record = awaitRecord(creditEvents, jobId.toString(), EventTypes.CREDIT_REJECTED);
        CreditRejected payload = EventEnvelope.fromJson(record.value()).payloadAs(CreditRejected.class);
        assertThat(payload.available()).isEqualTo(5);
        assertThat(payload.requested()).isEqualTo(12);
        assertThat(payload.reason()).isEqualTo("insufficient credits");
        assertThat(reservations.findByJobId(jobId)).isEmpty();
        assertThat(accounts.findByUserId(user).orElseThrow().getReserved()).isZero();
    }

    @Test
    void unknown_user_is_rejected_with_zero_available() {
        UUID jobId = UUID.randomUUID();
        kafka.send(jobCreatedRecord(jobCreated(jobId, "ghost-" + UUID.randomUUID(), 1)));

        ConsumerRecord<String, String> record = awaitRecord(creditEvents, jobId.toString(), EventTypes.CREDIT_REJECTED);
        CreditRejected payload = EventEnvelope.fromJson(record.value()).payloadAs(CreditRejected.class);
        assertThat(payload.available()).isZero();
        assertThat(payload.reason()).isEqualTo("no credit account");
    }

    @Test
    void poison_record_goes_straight_to_the_dead_letter_topic() {
        try (Consumer<String, String> dlt = consumerFactory.createConsumer("test-dlt-" + UUID.randomUUID(), "")) {
            dlt.subscribe(List.of(Topics.dlt(Topics.JOB_EVENTS)));
            String key = "poison-" + UUID.randomUUID();
            double dltBefore = metrics.counter("dlt.messages", "topic", Topics.JOB_EVENTS).count();

            kafka.send(new ProducerRecord<>(Topics.JOB_EVENTS, key, "this is not json"));

            ConsumerRecord<String, String> dead = awaitRecord(dlt, key, null);
            assertThat(dead.value()).isEqualTo("this is not json");
            assertThat(dead.partition()).isZero();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(metrics.counter("dlt.messages", "topic", Topics.JOB_EVENTS).count()).isGreaterThan(dltBefore));
        }
    }

    @Test
    void unknown_event_type_is_skipped_and_the_stream_continues() {
        String user = "after-unknown-" + UUID.randomUUID();
        topUp.topUp(user, 100);
        UUID jobId = UUID.randomUUID();
        EventEnvelope unknown = new EventEnvelope(java.util.UUID.randomUUID(), "SomethingFromTheFuture", 1,
                java.time.Instant.now(), "job", jobId.toString(), jobId.toString(), null, "job-service",
                com.remidosol.llmmcp.contracts.ContractsJson.MAPPER.createObjectNode());

        kafka.send(KafkaTestSupport.record(Topics.JOB_EVENTS, jobId.toString(), unknown));
        kafka.send(jobCreatedRecord(jobCreated(jobId, user, 3)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(reservations.findByJobId(jobId)).isPresent());
        assertThat(awaitRecord(creditEvents, jobId.toString(), EventTypes.CREDIT_RESERVED).key()).isEqualTo(jobId.toString());
    }
}
