package com.remidosol.llmmcp.llm.application.port;

import com.remidosol.llmmcp.llm.domain.LlmAttempt;

import java.util.Optional;
import java.util.UUID;

/** Outbound port for the attempt trail. */
public interface LlmAttemptRepository {

    LlmAttempt save(LlmAttempt attempt);

    Optional<LlmAttempt> findById(UUID id);

    long countByJobId(UUID jobId);
}
