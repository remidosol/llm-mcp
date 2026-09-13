package com.remidosol.llmmcp.credit.domain.event;

import java.util.UUID;

/** Domain event: the job could not be funded; the saga will move the job to REJECTED. */
public record CreditRejectedEvent(UUID jobId, String userId, String reason, long available, long requested,
                                  UUID causationId) {
}
