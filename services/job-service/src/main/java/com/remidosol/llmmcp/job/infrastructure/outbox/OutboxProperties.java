package com.remidosol.llmmcp.job.infrastructure.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Typed view of {@code app.outbox.*}. {@code publisher} selects who moves rows to Kafka: the
 * in-process poller ({@code polling}) or Debezium CDC ({@code cdc}, poller disabled) — ADR-0009.
 */
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(Publisher publisher, Duration pollInterval, int batchSize, Duration sendTimeout,
                               Duration retention) {

    public enum Publisher { POLLING, CDC }
}
