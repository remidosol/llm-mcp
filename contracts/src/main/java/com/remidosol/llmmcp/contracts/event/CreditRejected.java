package com.remidosol.llmmcp.contracts.event;

import java.util.UUID;

/** Emitted by credit-service when available credits (balance - reserved) are insufficient. */
public record CreditRejected(UUID jobId, String userId, String reason, long available, long requested) {
}
