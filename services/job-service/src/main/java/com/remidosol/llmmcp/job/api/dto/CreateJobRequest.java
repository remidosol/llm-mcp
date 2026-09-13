package com.remidosol.llmmcp.job.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for {@code POST /api/jobs}. Validation lives here, at the edge: by the time a
 * prompt reaches the application layer it is already known to be well-formed, so use cases carry
 * no defensive if-chains.
 */
public record CreateJobRequest(
        @NotBlank @Size(max = 8000) String prompt,
        @NotBlank @Pattern(regexp = "^(openai|gemini|fake):.+", message = "model must look like provider:model-id (openai:…, gemini:… or fake:…)") String model
) {
}
