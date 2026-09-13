package com.remidosol.llmmcp.job.infrastructure.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Boot has no built-in Kafka health indicator; this one asks the cluster for its node list with a
 * short timeout. Bean name {@code kafkaHealthIndicator} -> contributor key {@code kafka}, which the
 * readiness group includes: a pod that cannot reach Kafka takes no traffic.
 */
@Component("kafkaHealthIndicator")
public class KafkaHealthIndicator implements HealthIndicator {

    private static final int TIMEOUT_MS = 3000;

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var cluster = admin.describeCluster(new DescribeClusterOptions().timeoutMs(TIMEOUT_MS));
            int nodes = cluster.nodes().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).size();
            String clusterId = cluster.clusterId().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return Health.up().withDetail("clusterId", clusterId).withDetail("nodes", nodes).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down(e).build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
