package com.remidosol.llmmcp.job.application;

import com.remidosol.llmmcp.job.application.port.JobRepository;
import com.remidosol.llmmcp.job.application.port.JobResultRepository;
import com.remidosol.llmmcp.job.domain.JobStatus;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read side of the job API. It exists separately from {@link CreateJobService} so that caching
 * decorates ONLY reads: task 1.4 puts {@code @Cacheable} on {@link #getJob}, and the write path
 * never routes through a cache.
 */
@Service
public class JobQueryService {

    private final JobRepository jobRepository;
    private final JobResultRepository resultRepository;

    public JobQueryService(JobRepository jobRepository, JobResultRepository resultRepository) {
        this.jobRepository = jobRepository;
        this.resultRepository = resultRepository;
    }

    /** 404 until the job is COMPLETED — a late result is kept but not served here. */
    @Transactional(readOnly = true)
    public JobResultView getResult(UUID id) {
        boolean completed = jobRepository.findById(id)
                .map(job -> job.getStatus() == JobStatus.COMPLETED)
                .orElseThrow(() -> new JobNotFoundException(id));
        if (!completed) {
            throw new JobResultNotReadyException(id);
        }
        return resultRepository.findById(id).map(JobResultView::from)
                .orElseThrow(() -> new JobResultNotReadyException(id));
    }

    @Cacheable(cacheNames = "jobs", key = "#id")
    @Transactional(readOnly = true)
    public JobView getJob(UUID id) {
        return jobRepository.findById(id)
                .map(JobView::from)
                .orElseThrow(() -> new JobNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<JobView> listJobs(String userId, JobStatus status, int limit) {
        return jobRepository.findForUser(userId, status, limit).stream()
                .map(JobView::from)
                .toList();
    }
}
