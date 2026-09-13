package com.remidosol.llmmcp.job;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real infrastructure for tests — Testcontainers, never H2: the production database is
 * Postgres, so tests run against Postgres. {@code @ServiceConnection} feeds each container's
 * host/port/credentials straight into Boot's connection details — no property plumbing.
 *
 * <p>Images are pinned to the versions in docs/verified-versions.md; {@code :latest} is banned.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Testcontainers 2.x dropped the self-typed generic: PostgreSQLContainer is not generic anymore
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        // native image: ~1s startup vs ~5s for the JVM broker; test-only (docs/verified-versions.md)
        return new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"));
    }

    @Bean
    @ServiceConnection(name = "redis") // required: Boot cannot infer the image from a GenericContainer
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:8.10.1")).withExposedPorts(6379);
    }
}
