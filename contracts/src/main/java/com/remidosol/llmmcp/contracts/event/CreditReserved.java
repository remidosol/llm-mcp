package com.remidosol.llmmcp.contracts.event;

import java.util.UUID;

/**
 * Emitted by credit-service after a successful reservation. Carries prompt and model on purpose
 * (event-carried state transfer, ADR-0010) so llm-worker never calls job-service synchronously.
 */
public record CreditReserved(UUID jobId, String userId, UUID reservationId, long amount, String prompt, String model) {
}
