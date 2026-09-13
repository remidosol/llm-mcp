package com.remidosol.llmmcp.job.application;

import com.remidosol.llmmcp.job.domain.JobResult;

import java.time.Instant;
import java.util.UUID;

/** Read model for {@code GET /api/jobs/{id}/result} and the MCP resource (Phase 5). */
public record JobResultView(UUID jobId, String output, String provider, String model, Integer promptTokens,
                            Integer completionTokens, boolean late, Instant createdAt) {

    public static JobResultView from(JobResult result) {
        return new JobResultView(result.getJobId(), result.getOutput(), result.getProvider(), result.getModel(),
                result.getPromptTokens(), result.getCompletionTokens(), result.isLate(), result.getCreatedAt());
    }
}
