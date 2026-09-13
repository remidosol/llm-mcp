package com.remidosol.llmmcp.credit.application;

import com.remidosol.llmmcp.credit.application.port.CreditAccountRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side: cached 5 s (PRD §4.7) — short on purpose, balances change on every saga step. */
@Service
public class CreditQueryService {

    private final CreditAccountRepository accounts;

    public CreditQueryService(CreditAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Cacheable(cacheNames = CreditCacheEvictor.CREDITS_CACHE, key = "#userId")
    @Transactional(readOnly = true)
    public CreditView getCredits(String userId) {
        return accounts.findByUserId(userId)
                .map(CreditView::from)
                .orElseThrow(() -> new AccountNotFoundException(userId));
    }
}
