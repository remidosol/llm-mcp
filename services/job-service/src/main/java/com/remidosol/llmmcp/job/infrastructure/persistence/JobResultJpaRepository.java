package com.remidosol.llmmcp.job.infrastructure.persistence;

import com.remidosol.llmmcp.job.application.port.JobResultRepository;
import com.remidosol.llmmcp.job.domain.JobResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Persistence adapter for job results. */
public interface JobResultJpaRepository extends JpaRepository<JobResult, UUID>, JobResultRepository {
}
