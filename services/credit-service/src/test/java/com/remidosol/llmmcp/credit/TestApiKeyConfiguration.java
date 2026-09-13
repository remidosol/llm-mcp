package com.remidosol.llmmcp.credit;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Every TestRestTemplate call carries the ADMIN test key (admin implies user). Security tests that
 * need an anonymous or user-only client build their own RestTemplate without this header.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestApiKeyConfiguration {

    public static final String USER_KEY = "test-user-key";
    public static final String ADMIN_KEY = "test-admin-key";

    @Bean
    RestTemplateBuilder restTemplateBuilder() {
        return new RestTemplateBuilder().defaultHeader("X-API-Key", ADMIN_KEY);
    }
}
