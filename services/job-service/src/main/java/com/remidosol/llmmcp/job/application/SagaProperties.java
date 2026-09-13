package com.remidosol.llmmcp.job.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code app.saga.*}: how long a job may sit in CREDIT_RESERVED/PROCESSING, and how often we check. */
@ConfigurationProperties(prefix = "app.saga")
public record SagaProperties(Duration jobTimeout, Duration watchdogInterval) {
}
