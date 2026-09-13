package com.remidosol.llmmcp.credit.domain.event;

import java.util.UUID;

/** Domain event: the job completed, its actual cost was deducted and the hold released. */
public record CreditCapturedEvent(UUID jobId, UUID reservationId, long amount, UUID causationId) {
}
