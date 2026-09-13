package com.remidosol.llmmcp.credit.application;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.event.JobCompleted;
import com.remidosol.llmmcp.credit.application.port.CreditAccountRepository;
import com.remidosol.llmmcp.credit.application.port.CreditReservationRepository;
import com.remidosol.llmmcp.credit.domain.CreditAccount;
import com.remidosol.llmmcp.credit.domain.CreditReservation;
import com.remidosol.llmmcp.credit.domain.ReservationStatus;
import com.remidosol.llmmcp.credit.domain.event.CreditCapturedEvent;
import com.remidosol.llmmcp.credit.domain.event.CreditReleasedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Saga step 6: settle a reservation. Capture deducts the ACTUAL cost (possibly less than
 * the estimate — partial capture) and frees the whole hold; release is the compensation for a
 * failed or timed-out job and deducts nothing. Both are guarded: a reservation that is no longer
 * RESERVED (duplicate, or a late/out-of-order event) is logged, counted and ignored — never retried.
 */
@Service
public class SettleCreditService {

    private static final Logger log = LoggerFactory.getLogger(SettleCreditService.class);

    private final CreditAccountRepository accounts;
    private final CreditReservationRepository reservations;
    private final CreditCacheEvictor cache;
    private final ApplicationEventPublisher events;
    private final MeterRegistry metrics;

    public SettleCreditService(CreditAccountRepository accounts, CreditReservationRepository reservations,
                               CreditCacheEvictor cache, ApplicationEventPublisher events, MeterRegistry metrics) {
        this.accounts = accounts;
        this.reservations = reservations;
        this.cache = cache;
        this.events = events;
        this.metrics = metrics;
    }

    @Transactional
    public void capture(EventEnvelope trigger) {
        JobCompleted job = trigger.payloadAs(JobCompleted.class);
        settle(job.jobId(), "capture", (account, reservation) -> {
            long actual = job.actualCredits();
            account.capture(reservation.getAmount(), actual);
            reservation.capture(actual);
            events.publishEvent(new CreditCapturedEvent(job.jobId(), reservation.getId(), actual, trigger.eventId()));
        });
    }

    @Transactional
    public void release(UUID jobId, EventEnvelope trigger) {
        settle(jobId, "release", (account, reservation) -> {
            account.release(reservation.getAmount());
            reservation.release();
            events.publishEvent(new CreditReleasedEvent(jobId, reservation.getId(), reservation.getAmount(),
                    trigger.eventId()));
        });
    }

    private void settle(UUID jobId, String action, BiConsumer<CreditAccount, CreditReservation> effect) {
        Optional<CreditReservation> found = reservations.findByJobId(jobId);
        if (found.isEmpty()) {
            log.warn("{} for job {} ignored: no reservation", action, jobId);
            metrics.counter("saga.guard_rejected", "service", "credit", "reason", "no_reservation").increment();
            return;
        }
        CreditReservation reservation = found.get();
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            log.warn("{} for job {} ignored: reservation is {}", action, jobId, reservation.getStatus());
            metrics.counter("saga.guard_rejected", "service", "credit", "reason", "already_settled").increment();
            return;
        }
        CreditAccount account = accounts.findForUpdate(reservation.getUserId())
                .orElseThrow(() -> new IllegalStateException("account vanished: " + reservation.getUserId()));
        effect.accept(account, reservation);
        cache.evict(reservation.getUserId());
        metrics.counter("saga.transition", "service", "credit", "to", action).increment();
    }
}
