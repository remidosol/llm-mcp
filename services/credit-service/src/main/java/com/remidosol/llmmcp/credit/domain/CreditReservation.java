package com.remidosol.llmmcp.credit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One job's hold on credits. Exists as its own row (not just a number on the account) so that
 * capture/release know exactly how much to give back per job, and so {@code job_id UNIQUE} makes a
 * duplicate reservation impossible at the storage level.
 */
@Entity
@Table(name = "credit_reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditReservation {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private long amount;

    @Column(name = "captured_amount")
    private Long capturedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CreditReservation create(UUID id, UUID jobId, String userId, long amount) {
        CreditReservation reservation = new CreditReservation();
        reservation.id = id;
        reservation.jobId = jobId;
        reservation.userId = userId;
        reservation.amount = amount;
        reservation.status = ReservationStatus.RESERVED;
        Instant now = Instant.now();
        reservation.createdAt = now;
        reservation.updatedAt = now;
        return reservation;
    }

    public void capture(long actualAmount) {
        assertReserved("capture");
        this.capturedAmount = actualAmount;
        this.status = ReservationStatus.CAPTURED;
        touch();
    }

    public void release() {
        assertReserved("release");
        this.status = ReservationStatus.RELEASED;
        touch();
    }

    private void assertReserved(String attempted) {
        if (status != ReservationStatus.RESERVED) {
            throw new InvalidReservationStateException(status, attempted);
        }
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
