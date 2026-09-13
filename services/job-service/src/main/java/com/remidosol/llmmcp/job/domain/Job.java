package com.remidosol.llmmcp.job.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The Job aggregate — also a JPA entity, an accepted coupling (ADR-0012): pragmatic hexagonal-lite
 * tolerates {@code jakarta.persistence} in the domain so we avoid a parallel mapping layer.
 * All state changes go through {@link #transitionTo}, which is the single door the transition
 * table guards; nothing else may mutate {@code status}.
 *
 * <p>Lombok is allowed on JPA entities only (ADR-0004): {@code @Getter} kills accessor noise and
 * {@code @NoArgsConstructor(PROTECTED)} satisfies JPA's no-arg constructor requirement without
 * exposing it to application code — creation goes through {@link #create}.
 */
@Entity
@Table(name = "job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String prompt;

    @Column(nullable = false)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "estimated_credits", nullable = false)
    private int estimatedCredits;

    @Column(name = "actual_credits")
    private Integer actualCredits;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic locking: concurrent saga updates fail fast instead of silently overwriting.
     * Wrapper type on purpose: with a primitive, Spring Data cannot use the version for is-new
     * detection and — since the UUID id is pre-assigned — would merge() (SELECT + INSERT) instead
     * of persist() on every save.
     */
    @Version
    private Long version;

    public static Job create(UUID id, String userId, String prompt, String model, int estimatedCredits) {
        Job job = new Job();
        job.id = id;
        job.userId = userId;
        job.prompt = prompt;
        job.model = model;
        job.status = JobStatus.CREATED;
        job.estimatedCredits = estimatedCredits;
        Instant now = Instant.now();
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    /**
     * The single entry point for saga state changes. Throws {@link InvalidTransitionException}
     * and leaves the entity untouched when the transition table rejects the pair.
     */
    public void transitionTo(JobStatus target) {
        JobTransitions.assertAllowed(this.status, target);
        this.status = target;
        this.updatedAt = Instant.now();
    }

    /** Why a job ended in REJECTED / FAILED / TIMED_OUT — for the API and the demo trail. */
    public void recordFailure(String reason) {
        this.failureReason = reason;
        touch();
    }

    /** The measured cost reported by llm-worker; the estimate stays for comparison. */
    public void recordActualCredits(int actualCredits) {
        this.actualCredits = actualCredits;
        touch();
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
