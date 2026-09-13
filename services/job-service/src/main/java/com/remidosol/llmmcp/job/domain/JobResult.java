package com.remidosol.llmmcp.job.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The model output for a job. Stored even when it arrives after the watchdog gave up
 * ({@code late = true}): the work was done and paid for by the provider, so it is kept for
 * inspection, but the saga does not reopen the job (ADR-0016).
 */
@Entity
@Table(name = "job_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobResult {

    @Id
    @Column(name = "job_id")
    private UUID jobId;

    @Column(nullable = false)
    private String output;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(nullable = false)
    private boolean late;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static JobResult of(UUID jobId, String output, String provider, String model,
                               Integer promptTokens, Integer completionTokens, boolean late) {
        JobResult result = new JobResult();
        result.jobId = jobId;
        result.output = output;
        result.provider = provider;
        result.model = model;
        result.promptTokens = promptTokens;
        result.completionTokens = completionTokens;
        result.late = late;
        result.createdAt = Instant.now();
        return result;
    }
}
