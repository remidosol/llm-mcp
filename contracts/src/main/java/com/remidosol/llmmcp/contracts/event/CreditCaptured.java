package com.remidosol.llmmcp.contracts.event;

import java.util.UUID;

/** Emitted by credit-service after the actual cost was deducted and the reservation closed. */
public record CreditCaptured(UUID jobId, UUID reservationId, long amount) {
}
