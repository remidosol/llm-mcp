package com.remidosol.llmmcp.llm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One provider call for one job. Persisted so that retries, duplicates and chaos kills leave a
 * trail: "how many times did we call the provider for this job, and what came back?" is the first
 * question in any incident.
 */
@Entity
@Table(name = "llm_attempt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmAttempt {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status;

    private String error;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public static LlmAttempt start(UUID id, UUID jobId, String provider, String model, int attemptNo) {
        LlmAttempt attempt = new LlmAttempt();
        attempt.id = id;
        attempt.jobId = jobId;
        attempt.provider = provider;
        attempt.model = model;
        attempt.attemptNo = attemptNo;
        attempt.status = AttemptStatus.STARTED;
        attempt.startedAt = Instant.now();
        return attempt;
    }

    public void succeed(LlmResult result) {
        this.status = AttemptStatus.SUCCEEDED;
        this.promptTokens = result.promptTokens();
        this.completionTokens = result.completionTokens();
        this.finishedAt = Instant.now();
    }

    public void fail(String error) {
        this.status = AttemptStatus.FAILED;
        this.error = error;
        this.finishedAt = Instant.now();
    }
}
