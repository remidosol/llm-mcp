package com.remidosol.llmmcp.credit.infrastructure.outbox;

import com.remidosol.llmmcp.contracts.AggregateTypes;
import com.remidosol.llmmcp.contracts.EventEnvelope;
import com.remidosol.llmmcp.contracts.EventTypes;
import com.remidosol.llmmcp.contracts.event.CreditCaptured;
import com.remidosol.llmmcp.contracts.event.CreditRejected;
import com.remidosol.llmmcp.contracts.event.CreditReleased;
import com.remidosol.llmmcp.contracts.event.CreditReserved;
import com.remidosol.llmmcp.credit.domain.event.CreditCapturedEvent;
import com.remidosol.llmmcp.credit.domain.event.CreditRejectedEvent;
import com.remidosol.llmmcp.credit.domain.event.CreditReleasedEvent;
import com.remidosol.llmmcp.credit.domain.event.CreditReservedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Maps credit domain events to contract envelopes inside the reservation transaction (task 3.4):
 * the inbox claim, the account update, the reservation row and the outbox row commit together.
 */
@Component
class CreditEventsOutboxListener {

    static final String PRODUCER = "credit-service";

    private final OutboxWriter outbox;

    CreditEventsOutboxListener(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @EventListener
    public void on(CreditReservedEvent event) {
        String jobId = event.jobId().toString();
        outbox.append(EventEnvelope.create(EventTypes.CREDIT_RESERVED, AggregateTypes.CREDIT, jobId, jobId,
                event.causationId(), PRODUCER,
                new CreditReserved(event.jobId(), event.userId(), event.reservationId(), event.amount(),
                        event.prompt(), event.model())));
    }

    @EventListener
    public void on(CreditCapturedEvent event) {
        String jobId = event.jobId().toString();
        outbox.append(EventEnvelope.create(EventTypes.CREDIT_CAPTURED, AggregateTypes.CREDIT, jobId, jobId,
                event.causationId(), PRODUCER, new CreditCaptured(event.jobId(), event.reservationId(), event.amount())));
    }

    @EventListener
    public void on(CreditReleasedEvent event) {
        String jobId = event.jobId().toString();
        outbox.append(EventEnvelope.create(EventTypes.CREDIT_RELEASED, AggregateTypes.CREDIT, jobId, jobId,
                event.causationId(), PRODUCER, new CreditReleased(event.jobId(), event.reservationId(), event.amount())));
    }

    @EventListener
    public void on(CreditRejectedEvent event) {
        String jobId = event.jobId().toString();
        outbox.append(EventEnvelope.create(EventTypes.CREDIT_REJECTED, AggregateTypes.CREDIT, jobId, jobId,
                event.causationId(), PRODUCER,
                new CreditRejected(event.jobId(), event.userId(), event.reason(), event.available(), event.requested())));
    }
}
