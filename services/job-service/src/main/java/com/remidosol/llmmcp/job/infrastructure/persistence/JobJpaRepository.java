package com.remidosol.llmmcp.job.infrastructure.persistence;

import com.remidosol.llmmcp.job.application.port.JobRepository;
import com.remidosol.llmmcp.job.domain.Job;
import com.remidosol.llmmcp.job.domain.JobStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The persistence adapter: Spring Data derives the queries, and extending both {@code JpaRepository}
 * and the application's {@link JobRepository} port makes this interface the living proof of the
 * ArchUnit rule — application talks to the port, infrastructure implements it.
 */
public interface JobJpaRepository extends JpaRepository<Job, UUID>, JobRepository {

    List<Job> findByUserIdOrderByCreatedAtDesc(String userId, Limit limit);

    List<Job> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, JobStatus status, Limit limit);

    @Override
    default List<Job> findForUser(String userId, JobStatus status, int limit) {
        return status == null
                ? findByUserIdOrderByCreatedAtDesc(userId, Limit.of(limit))
                : findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, Limit.of(limit));
    }

    // lock.timeout = -2 is Hibernate's SKIP_LOCKED: "for update skip locked" on Postgres
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select j from Job j where j.status in :statuses and j.updatedAt < :cutoff order by j.updatedAt")
    List<Job> findStuck(@Param("statuses") Collection<JobStatus> statuses, @Param("cutoff") Instant cutoff,
                        Pageable pageable);

    @Override
    default List<Job> findStuckForUpdate(Collection<JobStatus> statuses, Instant cutoff, int limit) {
        return findStuck(statuses, cutoff, PageRequest.of(0, limit));
    }
}
