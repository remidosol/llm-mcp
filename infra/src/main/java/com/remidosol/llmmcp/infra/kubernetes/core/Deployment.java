package com.remidosol.llmmcp.infra.kubernetes.core;

import com.remidosol.llmmcp.infra.kubernetes.Metadata;
import io.cdktn.providers.kubernetes.deployment.DeploymentConfig;
import io.cdktn.providers.kubernetes.deployment.DeploymentMetadata;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpec;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecSelector;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpec;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import software.constructs.Construct;

/**
 * Deployment with the house rules baked in (port of {@code KubernetesDeployment}): selector = the
 * standard labels, 3 revisions of history, wait for the rollout (a failed rollout fails the apply,
 * which is the CI signal we want), Reloader lifecycle, hardened pod template.
 */
public class Deployment extends io.cdktn.providers.kubernetes.deployment.Deployment {

    private final Metadata metadata;

    public Deployment(Construct scope, String id, KubernetesProvider provider, Metadata metadata, int replicas,
                      DeploymentSpecTemplateSpec podSpec) {
        super(scope, id, DeploymentConfig.builder()
                .provider(provider)
                .waitForRollout(true)
                .lifecycle(Pod.lifecycle())
                .metadata(DeploymentMetadata.builder()
                        .name(metadata.name()).namespace(metadata.namespace())
                        .labels(metadata.labels()).annotations(Pod.metadata(metadata).annotations())
                        .build())
                .spec(DeploymentSpec.builder()
                        .replicas(String.valueOf(replicas))
                        .revisionHistoryLimit(3)
                        .selector(DeploymentSpecSelector.builder().matchLabels(metadata.labels()).build())
                        .template(Pod.template(metadata, podSpec))
                        .build())
                .build());
        this.metadata = metadata;
    }

    /** {@code objectMetadata}, not {@code metadata}: jsii would treat a {@code metadata()} method as an override of the resource property. */
    public Metadata objectMetadata() {
        return metadata;
    }
}
