package com.remidosol.llmmcp.credit.application;

import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.UuidV7;
import com.remidosol.llmmcp.contracts.event.JobCreated;
import com.remidosol.llmmcp.credit.application.port.CreditAccountRepository;
import com.remidosol.llmmcp.credit.application.port.CreditReservationRepository;
import com.remidosol.llmmcp.credit.domain.CreditAccount;
import com.remidosol.llmmcp.credit.domain.CreditReservation;
import com.remidosol.llmmcp.credit.domain.event.CreditRejectedEvent;
import com.remidosol.llmmcp.credit.domain.event.CreditReservedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Saga step 2: react to {@code JobCreated} by holding credits. The account row is
 * locked with {@code SELECT … FOR UPDATE} so two reservations for the same user cannot both read
 * the old {@code available} and over-commit — the classic lost-update anomaly of sagas, solved
 * here with a pessimistic lock inside one short transaction.
 */
@Service
public class ReserveCreditService {

    private static final Logger log = LoggerFactory.getLogger(ReserveCreditService.class);

    private final CreditAccountRepository accounts;
    private final CreditReservationRepository reservations;
    private final CreditCacheEvictor cache;
    private final ApplicationEventPublisher events;

    public ReserveCreditService(CreditAccountRepository accounts, CreditReservationRepository reservations,
                                CreditCacheEvictor cache, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.reservations = reservations;
        this.cache = cache;
        this.events = events;
    }

    @Transactional
    public void reserve(EventEnvelope trigger) {
        JobCreated job = trigger.payloadAs(JobCreated.class);
        long requested = job.estimatedCredits();

        if (reservations.findByJobId(job.jobId()).isPresent()) {
            // belt and braces: the inbox normally catches this; job_id UNIQUE would too
            log.warn("reservation for job {} already exists; ignoring duplicate JobCreated", job.jobId());
            return;
        }

        Optional<CreditAccount> locked = accounts.findForUpdate(job.userId());
        long available = locked.map(CreditAccount::available).orElse(0L);
        if (locked.isEmpty() || available < requested) {
            String reason = locked.isEmpty() ? "no credit account" : "insufficient credits";
            log.info("rejecting job {} for {}: {} (available={}, requested={})", job.jobId(), job.userId(),
                    reason, available, requested);
            events.publishEvent(new CreditRejectedEvent(job.jobId(), job.userId(), reason, available, requested,
                    trigger.eventId()));
            return;
        }

        CreditAccount account = locked.get();
        account.reserve(requested);
        UUID reservationId = UuidV7.generate();
        reservations.save(CreditReservation.create(reservationId, job.jobId(), job.userId(), requested));
        cache.evict(job.userId());
        events.publishEvent(new CreditReservedEvent(job.jobId(), job.userId(), reservationId, requested,
                job.prompt(), job.model(), trigger.eventId()));
    }
}
