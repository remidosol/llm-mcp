package com.remidosol.llmmcp.contracts.event;

import java.util.UUID;

/** Emitted by credit-service after a reservation was released (compensation). */
public record CreditReleased(UUID jobId, UUID reservationId, long amount) {
}
