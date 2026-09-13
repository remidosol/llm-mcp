package com.remidosol.llmmcp.job.application;

import com.remidosol.llmmcp.job.application.port.JobRepository;
import com.remidosol.llmmcp.job.domain.Job;
import com.remidosol.llmmcp.job.domain.JobStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The single application-level door for saga state changes. It exists now (Phase 1) so that the
 * cache eviction point is nailed down before the saga arrives: Phase 4 event handlers will call
 * exactly this method, and task 1.4 hangs {@code @CacheEvict} on it — one door, one eviction.
 *
 * <p>Note there is no explicit save: the entity is managed inside the transaction, and JPA dirty
 * checking flushes the change on commit (see java-for-node-devs.md).
 */
@Service
public class JobTransitionService {

    private final JobRepository jobRepository;

    public JobTransitionService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @CacheEvict(cacheNames = "jobs", key = "#jobId")
    @Transactional
    public void transition(UUID jobId, JobStatus target) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        job.transitionTo(target);
    }
}
