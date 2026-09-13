package com.remidosol.llmmcp.credit.application;

import com.remidosol.llmmcp.credit.application.port.CreditAccountRepository;
import com.remidosol.llmmcp.credit.domain.CreditAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin use case: add credits, opening the account on first use (Phase 5 puts an admin key on it). */
@Service
public class TopUpService {

    private final CreditAccountRepository accounts;
    private final CreditCacheEvictor cache;

    public TopUpService(CreditAccountRepository accounts, CreditCacheEvictor cache) {
        this.accounts = accounts;
        this.cache = cache;
    }

    @Transactional
    public CreditView topUp(String userId, long amount) {
        CreditAccount account = accounts.findForUpdate(userId)
                .orElseGet(() -> accounts.save(CreditAccount.open(userId, 0)));
        account.topUp(amount);
        cache.evict(userId);
        return CreditView.from(account);
    }
}
