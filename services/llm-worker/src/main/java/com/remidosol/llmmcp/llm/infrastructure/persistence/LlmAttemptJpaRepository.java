package com.remidosol.llmmcp.llm.infrastructure.persistence;

import com.remidosol.llmmcp.llm.application.port.LlmAttemptRepository;
import com.remidosol.llmmcp.llm.domain.LlmAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Persistence adapter for the attempt trail; countByJobId is a derived query. */
public interface LlmAttemptJpaRepository extends JpaRepository<LlmAttempt, UUID>, LlmAttemptRepository {
}
