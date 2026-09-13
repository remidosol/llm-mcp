package com.remidosol.llmmcp.credit;

import com.remidosol.llmmcp.contracts.AggregateTypes;
import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.Topics;
import com.remidosol.llmmcp.contracts.event.CreditCaptured;
import com.remidosol.llmmcp.contracts.event.JobCompleted;
import com.remidosol.llmmcp.contracts.event.JobFailed;
import com.remidosol.llmmcp.contracts.event.JobTimedOut;
import com.remidosol.llmmcp.credit.application.TopUpService;
import com.remidosol.llmmcp.credit.application.port.CreditAccountRepository;
import com.remidosol.llmmcp.credit.application.port.CreditReservationRepository;
import com.remidosol.llmmcp.credit.domain.ReservationStatus;
import org.apache.kafka.clients.consumer.Consumer;
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
import static com.remidosol.llmmcp.credit.KafkaTestSupport.record;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Task 4.3: capture (incl. partial), release (compensation), and the settle-once guards. */
class CreditSettlementIntegrationTest extends AbstractIntegrationTest {

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
    void JobCompleted_captures_the_actual_cost_and_frees_the_whole_hold() {
        String user = "capture-" + UUID.randomUUID();
        UUID jobId = reserve(user, 100, 12);

        EventEnvelope completed = envelope(EventTypes.JOB_COMPLETED, jobId, new JobCompleted(jobId, user, 7));
        kafka.send(record(Topics.JOB_EVENTS, jobId.toString(), completed));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(reservations.findByJobId(jobId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CAPTURED));
        var account = accounts.findByUserId(user).orElseThrow();
        assertThat(account.getBalance()).isEqualTo(93);   // partial capture: 7 of the 12 held
        assertThat(account.getReserved()).isZero();
        assertThat(reservations.findByJobId(jobId).orElseThrow().getCapturedAmount()).isEqualTo(7);

        var captured = EventEnvelope.fromJson(awaitRecord(creditEvents, jobId.toString(), EventTypes.CREDIT_CAPTURED).value());
        assertThat(captured.causationId()).isEqualTo(completed.eventId());
        assertThat(captured.payloadAs(CreditCaptured.class).amount()).isEqualTo(7);
    }

    @Test
    void JobFailed_releases_the_hold_without_deducting() {
        String user = "release-" + UUID.randomUUID();
        UUID jobId = reserve(user, 100, 12);

        kafka.send(record(Topics.JOB_EVENTS, jobId.toString(),
                envelope(EventTypes.JOB_FAILED, jobId, new JobFailed(jobId, user, "boom"))));

        awaitRecord(creditEvents, jobId.toString(), EventTypes.CREDIT_RELEASED);
        var account = accounts.findByUserId(user).orElseThrow();
        assertThat(account.getBalance()).isEqualTo(100);
        assertThat(account.getReserved()).isZero();
        assertThat(reservations.findByJobId(jobId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void JobTimedOut_releases_and_a_later_JobCompleted_is_ignored() {
        String user = "timeout-" + UUID.randomUUID();
        UUID jobId = reserve(user, 100, 12);

        kafka.send(record(Topics.JOB_EVENTS, jobId.toString(),
                envelope(EventTypes.JOB_TIMED_OUT, jobId, new JobTimedOut(jobId, user))));
        awaitRecord(creditEvents, jobId.toString(), EventTypes.CREDIT_RELEASED);

        kafka.send(record(Topics.JOB_EVENTS, jobId.toString(),
                envelope(EventTypes.JOB_COMPLETED, jobId, new JobCompleted(jobId, user, 7)))); // late: must not capture
        await().during(Duration.ofSeconds(4)).atMost(Duration.ofSeconds(8)).until(() ->
                accounts.findByUserId(user).orElseThrow().getBalance() == 100);
        assertThat(reservations.findByJobId(jobId).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void duplicate_JobCompleted_captures_exactly_once() {
        String user = "dupcap-" + UUID.randomUUID();
        UUID jobId = reserve(user, 100, 12);
        EventEnvelope completed = envelope(EventTypes.JOB_COMPLETED, jobId, new JobCompleted(jobId, user, 7));

        kafka.send(record(Topics.JOB_EVENTS, jobId.toString(), completed));
        kafka.send(record(Topics.JOB_EVENTS, jobId.toString(), completed));

        awaitRecord(creditEvents, jobId.toString(), EventTypes.CREDIT_CAPTURED);
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).until(() ->
                accounts.findByUserId(user).orElseThrow().getBalance() == 93);
    }

    private UUID reserve(String user, long balance, int estimated) {
        topUp.topUp(user, balance);
        UUID jobId = UUID.randomUUID();
        kafka.send(jobCreatedRecord(jobCreated(jobId, user, estimated)));
        awaitRecord(creditEvents, jobId.toString(), EventTypes.CREDIT_RESERVED);
        return jobId;
    }

    private static EventEnvelope envelope(String type, UUID jobId, Object payload) {
        return EventEnvelope.create(type, AggregateTypes.JOB, jobId.toString(), jobId.toString(), null, "job-service", payload);
    }
}
