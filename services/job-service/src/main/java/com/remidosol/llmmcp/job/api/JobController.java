package com.remidosol.llmmcp.job.api;

import com.remidosol.llmmcp.job.api.dto.CreateJobRequest;
import com.remidosol.llmmcp.job.application.CreateJobService;
import com.remidosol.llmmcp.job.application.JobQueryService;
import com.remidosol.llmmcp.job.application.JobResultView;
import com.remidosol.llmmcp.job.application.JobView;
import com.remidosol.llmmcp.job.domain.JobStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Thin inbound adapter: translates HTTP to use-case calls and nothing else. The Phase 5 MCP tools
 * will call the SAME application services — that symmetry is why no business logic may live here.
 */
// No class-level @Validated: Spring Framework 7 validates handler-method parameters natively
// (HandlerMethodValidationException -> 400 problem+json). Adding @Validated would DISABLE that
// and route violations through the AOP proxy as ConstraintViolationException -> 500.
@RestController
@RequestMapping("/api/jobs")
class JobController {

    private final CreateJobService createJobService;
    private final JobQueryService jobQueryService;

    JobController(CreateJobService createJobService, JobQueryService jobQueryService) {
        this.createJobService = createJobService;
        this.jobQueryService = jobQueryService;
    }

    /**
     * 202 Accepted, not 201: the job is accepted for asynchronous processing — the saga decides
     * later whether it completes, fails or is rejected. The client polls {@code GET /api/jobs/{id}}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    JobView create(@RequestHeader("X-User-Id") String userId,
                   @Valid @RequestBody CreateJobRequest request) {
        return createJobService.create(userId, request.prompt(), request.model());
    }

    @GetMapping("/{id}")
    JobView get(@PathVariable UUID id) {
        return jobQueryService.getJob(id);
    }

    @GetMapping("/{id}/result")
    JobResultView result(@PathVariable UUID id) {
        return jobQueryService.getResult(id);
    }

    @GetMapping
    List<JobView> list(@RequestHeader("X-User-Id") String userId,
                       @RequestParam(required = false) JobStatus status,
                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return jobQueryService.listJobs(userId, status, limit);
    }
}
