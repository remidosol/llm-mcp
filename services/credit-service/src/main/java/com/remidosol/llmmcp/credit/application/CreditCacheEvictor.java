package com.remidosol.llmmcp.credit.application;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * A bean whose only job is to carry {@code @CacheEvict}. It exists because cache annotations work
 * through the Spring proxy: a service cannot evict by annotating a method it calls on itself, so
 * every write path (reserve, capture, release, top-up) calls this bean instead.
 */
@Component
public class CreditCacheEvictor {

    public static final String CREDITS_CACHE = "credits";

    @CacheEvict(cacheNames = CREDITS_CACHE, key = "#userId")
    public void evict(String userId) {
        // the annotation is the implementation
    }
}
