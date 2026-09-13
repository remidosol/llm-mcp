package com.remidosol.llmmcp.credit.domain.event;

import java.util.UUID;

/**
 * Domain event: credits were held for a job. Carries prompt/model so the infrastructure can build
 * the event-carried-state {@code CreditReserved} record (ADR-0010) and {@code causationId} so the
 * outgoing envelope points back at the {@code JobCreated} that triggered it.
 */
public record CreditReservedEvent(UUID jobId, String userId, UUID reservationId, long amount,
                                  String prompt, String model, UUID causationId) {
}
