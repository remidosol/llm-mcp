package com.remidosol.llmmcp.credit.application;

import com.remidosol.llmmcp.credit.domain.CreditAccount;

import java.io.Serializable;

/**
 * Read model for {@code GET /api/credits/{userId}} and the Redis cache. Exposes {@code available}
 * explicitly because the rule is that balance alone must never be read as spendable.
 */
public record CreditView(String userId, long balance, long reserved, long available) implements Serializable {

    public static CreditView from(CreditAccount account) {
        return new CreditView(account.getUserId(), account.getBalance(), account.getReserved(), account.available());
    }
}
