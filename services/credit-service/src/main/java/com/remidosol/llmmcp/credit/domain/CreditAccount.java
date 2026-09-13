package com.remidosol.llmmcp.credit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The credit ledger of one user. {@code reserved} is the saga's SEMANTIC LOCK (Richardson ch. 4):
 * credits promised to in-flight jobs are not spendable by others, yet nothing is deducted until the
 * job completes. Every reader must treat {@code balance - reserved} as the real number; every
 * mutation goes through the methods below so the invariant {@code 0 <= reserved <= balance + debt}
 * stays in one place.
 */
@Entity
@Table(name = "credit_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditAccount {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false)
    private long balance;

    @Column(nullable = false)
    private long reserved;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CreditAccount open(String userId, long initialBalance) {
        CreditAccount account = new CreditAccount();
        account.userId = userId;
        account.balance = initialBalance;
        account.reserved = 0;
        account.updatedAt = Instant.now();
        return account;
    }

    public long available() {
        return balance - reserved;
    }

    /** Holds {@code amount} for a job; fails when the available credits are insufficient. */
    public void reserve(long amount) {
        if (amount > available()) {
            throw new InsufficientCreditsException(available(), amount);
        }
        reserved += amount;
        touch();
    }

    /** Job completed: the ACTUAL cost is deducted, the whole reservation is freed (partial capture). */
    public void capture(long reservedAmount, long actualAmount) {
        reserved -= reservedAmount;
        balance -= actualAmount;
        touch();
    }

    /** Compensation: the job failed or timed out, nothing is deducted. */
    public void release(long reservedAmount) {
        reserved -= reservedAmount;
        touch();
    }

    public void topUp(long amount) {
        balance += amount;
        touch();
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
