package com.remidosol.llmmcp.job.infrastructure.persistence;

import com.remidosol.llmmcp.job.TestcontainersConfiguration;
import com.remidosol.llmmcp.job.application.port.JobRepository;
import com.remidosol.llmmcp.job.domain.Job;
import com.remidosol.llmmcp.job.domain.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence slice against REAL Postgres (the reason H2 is banned: this test exercises the actual
 * Flyway schema, jsonb-capable dialect and index definitions the service runs on).
 *
 * <p>Injected as the application PORT on purpose: calling {@code save} on the Spring Data
 * interface directly would be ambiguous between the port's {@code save(Job)} and
 * {@code CrudRepository.<S>save(S)} — through the port there is exactly one method.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class JobJpaRepositoryTest {

    @Autowired
    private JobRepository repository;

    @Test
    void findForUser_filters_by_user_status_and_limit() {
        Job first = repository.save(newJob("slice-u1"));
        Job second = repository.save(newJob("slice-u1"));
        repository.save(newJob("slice-u2"));

        assertThat(repository.findForUser("slice-u1", null, 10))
                .extracting(Job::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());

        assertThat(repository.findForUser("slice-u1", JobStatus.CREATED, 10)).hasSize(2);
        assertThat(repository.findForUser("slice-u1", JobStatus.COMPLETED, 10)).isEmpty();
        assertThat(repository.findForUser("slice-u1", null, 1)).hasSize(1);
    }

    private Job newJob(String userId) {
        return Job.create(UUID.randomUUID(), userId, "prompt", "fake:demo", 1);
    }
}
