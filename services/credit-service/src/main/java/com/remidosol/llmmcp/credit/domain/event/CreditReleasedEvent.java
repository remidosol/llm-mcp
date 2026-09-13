package com.remidosol.llmmcp.credit.domain.event;

import java.util.UUID;

/** Domain event: compensation — the job failed or timed out and the hold was given back. */
public record CreditReleasedEvent(UUID jobId, UUID reservationId, long amount, UUID causationId) {
}
