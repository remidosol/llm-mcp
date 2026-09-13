package com.remidosol.llmmcp.infra.kubernetes.construct;

import com.remidosol.llmmcp.infra.kubernetes.Metadata;
import com.remidosol.llmmcp.infra.kubernetes.core.Manifests;
import io.cdktn.providers.kubernetes.manifest.Manifest;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import software.constructs.Construct;
import io.cdktn.cdktn.ITerraformDependable;

import java.util.List;
import java.util.Map;

/**
 * KEDA ScaledObject with a Kafka-lag trigger (task 7.4 stretch, ADR-0024 companion): the worker's
 * right autoscaling signal is consumer lag on credit.events.v1, not CPU — an LLM call is I/O-bound.
 * KEDA itself is installed by Helm in MainStack; this is the CR the reference's KEDA constructs
 * would own (their operator/metrics/webhooks manifests are replaced by the chart).
 */
public class KedaScaledObject extends Construct {

    private final Manifest manifest;

    public KedaScaledObject(Construct scope, String id, KubernetesProvider provider, Metadata metadata, String deploymentName,
                            String bootstrapServers, String consumerGroup, String topic, int lagThreshold, int minReplicas,
                            int maxReplicas, List<? extends ITerraformDependable> dependsOn) {
        super(scope, id);
        this.manifest = Manifests.of(this, "scaled-object", provider, Map.of(
                "apiVersion", "keda.sh/v1alpha1",
                "kind", "ScaledObject",
                "metadata", Map.of("name", deploymentName, "namespace", metadata.namespace(), "labels", metadata.labels()),
                "spec", Map.of(
                        "scaleTargetRef", Map.of("name", deploymentName),
                        "minReplicaCount", minReplicas,
                        "maxReplicaCount", maxReplicas,
                        "cooldownPeriod", 120,
                        "triggers", List.of(Map.of(
                                "type", "kafka",
                                "metadata", Map.of(
                                        "bootstrapServers", bootstrapServers,
                                        "consumerGroup", consumerGroup,
                                        "topic", topic,
                                        "lagThreshold", String.valueOf(lagThreshold),
                                        "offsetResetPolicy", "earliest"))))), dependsOn);
    }

    public Manifest manifest() {
        return manifest;
    }
}
