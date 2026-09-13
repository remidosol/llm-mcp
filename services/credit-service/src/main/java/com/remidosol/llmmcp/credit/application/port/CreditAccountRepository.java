package com.remidosol.llmmcp.credit.application.port;

import com.remidosol.llmmcp.credit.domain.CreditAccount;

import java.util.Optional;

/** Outbound port for accounts; {@link #findForUpdate} is the pessimistic lock the saga relies on. */
public interface CreditAccountRepository {

    Optional<CreditAccount> findByUserId(String userId);

    /** {@code SELECT … FOR UPDATE}: serializes concurrent reservations on one account. */
    Optional<CreditAccount> findForUpdate(String userId);

    CreditAccount save(CreditAccount account);
}
