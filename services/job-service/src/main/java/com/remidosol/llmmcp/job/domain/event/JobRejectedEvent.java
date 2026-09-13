package com.remidosol.llmmcp.job.domain.event;

import java.util.UUID;

/** Domain event: credits could not be reserved; the saga ends before any LLM work. */
public record JobRejectedEvent(UUID jobId, String reason, UUID causationId) {
}
