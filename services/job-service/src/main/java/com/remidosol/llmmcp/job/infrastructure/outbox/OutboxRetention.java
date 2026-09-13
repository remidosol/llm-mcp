package com.remidosol.llmmcp.job.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Keeps the outbox small (PRD task 3.2 "retention job"). In polling mode only PUBLISHED rows are
 * deleted; in CDC mode Debezium never marks rows, so age is the criterion (the WAL already carried
 * them). The "insert then delete in the same transaction" trick would remove the row even earlier
 * with CDC — kept as a documented option, not applied, so the row stays inspectable locally.
 */
@Component
public class OutboxRetention {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetention.class);

    private final JdbcClient jdbc;
    private final OutboxProperties properties;

    public OutboxRetention(JdbcClient jdbc, OutboxProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.outbox.retention-cron:0 0 3 * * *}")
    @Transactional
    public void purge() {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(properties.retention()));
        int deleted = properties.publisher() == OutboxProperties.Publisher.CDC
                ? jdbc.sql("delete from outbox where created_at < :cutoff").param("cutoff", cutoff).update()
                : jdbc.sql("delete from outbox where published_at < :cutoff").param("cutoff", cutoff).update();
        log.info("outbox retention: deleted {} rows older than {}", deleted, properties.retention());
    }
}
