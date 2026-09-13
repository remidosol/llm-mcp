package com.remidosol.llmmcp.job.api.mcp;

import com.remidosol.llmmcp.job.api.dto.CreateJobRequest;
import com.remidosol.llmmcp.job.application.CreateJobService;
import com.remidosol.llmmcp.job.application.JobQueryService;
import com.remidosol.llmmcp.job.application.JobResultView;
import com.remidosol.llmmcp.job.application.JobView;
import com.remidosol.llmmcp.job.domain.JobStatus;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.validation.Validator;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The MCP surface of job-service (ADR-0017): a second inbound adapter next to the REST
 * controller, calling the SAME application services. Spring AI's annotation scanner turns each
 * annotated method into a tool / resource / prompt / completion spec; the transport is stateless
 * Streamable HTTP on {@code /mcp}. Exceptions thrown here become {@code isError} tool results —
 * an LLM client sees the message and can correct its call.
 */
@Component
public class JobMcpServer {

    static final String RESULT_URI_TEMPLATE = "job://{jobId}/result";

    private final CreateJobService createJobService;
    private final JobQueryService jobQueryService;
    private final Validator validator;
    private final JsonMapper json;
    private final List<String> knownModels;

    public JobMcpServer(CreateJobService createJobService, JobQueryService jobQueryService, Validator validator,
                        JsonMapper json, @Value("${app.mcp.models}") List<String> knownModels) {
        this.createJobService = createJobService;
        this.jobQueryService = jobQueryService;
        this.validator = validator;
        this.json = json;
        this.knownModels = knownModels;
    }

    @McpTool(name = "create_job", description = "Submit a prompt as an asynchronous LLM job for a user. Returns the "
            + "accepted job (status CREATED). Poll get_job until COMPLETED, then read job://{jobId}/result.")
    public JobView createJob(
            @McpToolParam(description = "Owner of the job = credit account id, e.g. u1") String userId,
            @McpToolParam(description = "The prompt to run") String prompt,
            @McpToolParam(description = "provider:model-id, e.g. fake:demo, openai:gpt-4o-mini, gemini:gemini-2.5-flash") String model) {
        CreateJobRequest request = new CreateJobRequest(prompt, model);
        var violations = validator.validate(request); // the REST path validates via @Valid; MCP calls do it explicitly
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        return createJobService.create(userId, request.prompt(), request.model());
    }

    @McpTool(name = "get_job", description = "Current state of a job: status (CREATED, CREDIT_RESERVED, PROCESSING, "
            + "COMPLETED, FAILED, REJECTED, TIMED_OUT), estimated/actual credits and failure reason.")
    public JobView getJob(@McpToolParam(description = "Job id (UUID)") String jobId) {
        return jobQueryService.getJob(parseJobId(jobId));
    }

    @McpTool(name = "list_jobs", description = "Recent jobs of a user, newest first, optionally filtered by status.")
    public List<JobView> listJobs(
            @McpToolParam(description = "Credit account id, e.g. u1") String userId,
            @McpToolParam(description = "Optional status filter, e.g. COMPLETED", required = false) String status,
            @McpToolParam(description = "Max rows, 1-100 (default 20)", required = false) Integer limit) {
        JobStatus filter = status == null || status.isBlank() ? null : JobStatus.valueOf(status.toUpperCase(Locale.ROOT));
        int max = limit == null ? 20 : Math.clamp(limit, 1, 100);
        return jobQueryService.listJobs(userId, filter, max);
    }

    @McpResource(uri = RESULT_URI_TEMPLATE, name = "job-result", mimeType = "application/json",
            description = "The LLM output of a COMPLETED job with provider, model and token usage. "
                    + "Reading it before completion is an error (the job is still running, failed or timed out).")
    public McpSchema.ReadResourceResult jobResult(String jobId) {
        JobResultView result = jobQueryService.getResult(parseJobId(jobId));
        String uri = RESULT_URI_TEMPLATE.replace("{jobId}", jobId);
        McpSchema.TextResourceContents contents = McpSchema.TextResourceContents.builder(uri, json.writeValueAsString(result))
                .mimeType("application/json")
                .build();
        return McpSchema.ReadResourceResult.builder(List.of(contents)).build();
    }

    @McpPrompt(name = "compare_models", description = "Run one prompt on two models through this server and compare "
            + "the answers side by side.")
    public McpSchema.GetPromptResult compareModels(
            @McpArg(name = "prompt", description = "The prompt to run on both models") String prompt,
            @McpArg(name = "userId", description = "Credit account to charge (default u1)") String userId,
            @McpArg(name = "modelA", description = "First provider:model (default fake:demo)") String modelA,
            @McpArg(name = "modelB", description = "Second provider:model (default fake:echo)") String modelB) {
        String user = userId == null || userId.isBlank() ? "u1" : userId;
        String a = modelA == null || modelA.isBlank() ? "fake:demo" : modelA;
        String b = modelB == null || modelB.isBlank() ? "fake:echo" : modelB;
        String text = """
                Compare two models on the same prompt using the job-service tools.
                1. Call create_job twice for user "%s": model "%s" and model "%s", both with this prompt:
                   %s
                2. Poll get_job for each job id until the status is COMPLETED, FAILED, REJECTED or TIMED_OUT.
                3. For every COMPLETED job read the resource job://<jobId>/result.
                4. Present a side-by-side comparison: output quality, completion tokens and actual credits charged.
                   If a job did not complete, report its status and failureReason instead.
                """.formatted(user, a, b, prompt);
        McpSchema.PromptMessage message = McpSchema.PromptMessage.builder(McpSchema.Role.USER,
                McpSchema.TextContent.builder(text).build()).build();
        return McpSchema.GetPromptResult.builder(List.of(message))
                .description("Compare two models on one prompt")
                .build();
    }

    /** Argument completion for the prompt: known provider:model ids filtered by what the user typed so far. */
    @McpComplete(prompt = "compare_models")
    public List<String> completeModel(McpSchema.CompleteRequest.CompleteArgument argument) {
        if (!argument.name().startsWith("model")) {
            return List.of();
        }
        String typed = argument.value() == null ? "" : argument.value().toLowerCase(Locale.ROOT);
        return knownModels.stream().filter(m -> m.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }

    private static UUID parseJobId(String jobId) {
        try {
            return UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("jobId must be a UUID, got '" + jobId + "'");
        }
    }
}
