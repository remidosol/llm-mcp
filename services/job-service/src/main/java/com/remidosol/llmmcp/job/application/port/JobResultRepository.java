package com.remidosol.llmmcp.job.application.port;

import com.remidosol.llmmcp.job.domain.JobResult;

import java.util.Optional;
import java.util.UUID;

/** Outbound port for stored model outputs. */
public interface JobResultRepository {

    JobResult save(JobResult result);

    Optional<JobResult> findById(UUID jobId);

    boolean existsById(UUID jobId);
}
