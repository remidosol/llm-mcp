package com.remidosol.llmmcp.infra.kubernetes.core;

import com.remidosol.llmmcp.infra.kubernetes.Metadata;
import io.cdktn.cdktn.TerraformResourceLifecycle;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplate;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateMetadata;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpec;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecAffinity;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecAffinityPodAntiAffinity;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecAffinityPodAntiAffinityRequiredDuringSchedulingIgnoredDuringExecution;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecAffinityPodAntiAffinityRequiredDuringSchedulingIgnoredDuringExecutionLabelSelector;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainer;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerSecurityContext;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerSecurityContextCapabilities;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecSecurityContext;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecSecurityContextSeccompProfile;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecTopologySpreadConstraint;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecTopologySpreadConstraintLabelSelector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pod-template defaults (port of the reference {@code KubernetesPod} helpers), applied by the
 * workload constructs: Reloader + OTel-logs annotations, non-root/no-privilege security contexts
 * with a read-only root filesystem, pod anti-affinity on the node and zone-aware topology spread —
 * the hardening every Deployment gets without repeating it. The Java provider types are per-workload
 * (DeploymentSpecTemplateSpec…), so the helpers target Deployment; StatefulSet/DaemonSet mirror them.
 */
public final class Pod {

    public static final String NON_ROOT_UID = "65534";

    private Pod() {
    }

    /** Reloader rewrites this annotation on every config change; Terraform must not fight it. */
    public static TerraformResourceLifecycle lifecycle() {
        return TerraformResourceLifecycle.builder()
                .ignoreChanges(List.of("spec[0].template[0].metadata[0].annotations[\"reloader.stakater.com/last-reloaded-from\"]"))
                .build();
    }

    public static Metadata metadata(Metadata metadata) {
        return metadata.withAnnotations(Map.of("reloader.stakater.com/auto", "true", "opentelemetry.io/logs", "true"));
    }

    public static DeploymentSpecTemplateSpecAffinity affinity(Metadata metadata) {
        return DeploymentSpecTemplateSpecAffinity.builder()
                .podAntiAffinity(DeploymentSpecTemplateSpecAffinityPodAntiAffinity.builder()
                        .requiredDuringSchedulingIgnoredDuringExecution(List.of(
                                DeploymentSpecTemplateSpecAffinityPodAntiAffinityRequiredDuringSchedulingIgnoredDuringExecution.builder()
                                        .labelSelector(List.of(DeploymentSpecTemplateSpecAffinityPodAntiAffinityRequiredDuringSchedulingIgnoredDuringExecutionLabelSelector.builder()
                                                .matchLabels(metadata.labels()).build()))
                                        .topologyKey("kubernetes.io/hostname")
                                        .build()))
                        .build())
                .build();
    }

    public static List<DeploymentSpecTemplateSpecTopologySpreadConstraint> topologySpread(Metadata metadata) {
        return List.of(DeploymentSpecTemplateSpecTopologySpreadConstraint.builder()
                .labelSelector(List.of(DeploymentSpecTemplateSpecTopologySpreadConstraintLabelSelector.builder()
                        .matchLabels(metadata.labels()).build()))
                .topologyKey("topology.kubernetes.io/zone")
                .whenUnsatisfiable("ScheduleAnyway")
                .maxSkew(1)
                .build());
    }

    public static DeploymentSpecTemplateSpecSecurityContext podSecurityContext() {
        return DeploymentSpecTemplateSpecSecurityContext.builder()
                .runAsNonRoot(true)
                .runAsUser(NON_ROOT_UID)
                .runAsGroup(NON_ROOT_UID)
                .fsGroup(NON_ROOT_UID)
                .seccompProfile(DeploymentSpecTemplateSpecSecurityContextSeccompProfile.builder().type("RuntimeDefault").build())
                .build();
    }

    public static DeploymentSpecTemplateSpecContainerSecurityContext containerSecurityContext() {
        return DeploymentSpecTemplateSpecContainerSecurityContext.builder()
                .allowPrivilegeEscalation(false)
                .privileged(false)
                .readOnlyRootFilesystem(true)
                .capabilities(DeploymentSpecTemplateSpecContainerSecurityContextCapabilities.builder().drop(List.of("ALL")).build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castList(Object value) {
        return (List<T>) value;
    }

    /** A hardened container: the caller's builder plus the default security context unless it set one. */
    public static DeploymentSpecTemplateSpecContainer harden(DeploymentSpecTemplateSpecContainer container) {
        if (container.getSecurityContext() != null) {
            return container;
        }
        return DeploymentSpecTemplateSpecContainer.builder()
                .name(container.getName()).image(container.getImage()).imagePullPolicy(container.getImagePullPolicy())
                .args(container.getArgs()).command(container.getCommand())
                .env(castList(container.getEnv())).envFrom(castList(container.getEnvFrom())).port(castList(container.getPort()))
                .resources(container.getResources())
                .startupProbe(container.getStartupProbe()).livenessProbe(container.getLivenessProbe())
                .readinessProbe(container.getReadinessProbe())
                .volumeMount(castList(container.getVolumeMount()))
                .securityContext(containerSecurityContext())
                .build();
    }

    /** Full pod template: standardized metadata + hardened spec around the caller's containers/volumes. */
    public static DeploymentSpecTemplate template(Metadata metadata, DeploymentSpecTemplateSpec spec) {
        Metadata podMetadata = metadata(metadata);
        Map<String, String> labels = new LinkedHashMap<>(podMetadata.labels());
        return DeploymentSpecTemplate.builder()
                .metadata(DeploymentSpecTemplateMetadata.builder()
                        .labels(labels).annotations(podMetadata.annotations()).build())
                .spec(DeploymentSpecTemplateSpec.builder()
                        .container(spec.getContainer() == null ? List.of()
                                : ((List<?>) spec.getContainer()).stream()
                                .map(c -> harden((DeploymentSpecTemplateSpecContainer) c)).toList())
                        .volume(castList(spec.getVolume()))
                        .serviceAccountName(spec.getServiceAccountName())
                        .terminationGracePeriodSeconds(spec.getTerminationGracePeriodSeconds())
                        .nodeSelector(spec.getNodeSelector())
                        .toleration(castList(spec.getToleration()))
                        .priorityClassName(spec.getPriorityClassName())
                        .affinity(spec.getAffinity() != null ? spec.getAffinity() : affinity(metadata))
                        .securityContext(spec.getSecurityContext() != null ? spec.getSecurityContext() : podSecurityContext())
                        .topologySpreadConstraint(spec.getTopologySpreadConstraint() != null
                                ? castList(spec.getTopologySpreadConstraint()) : topologySpread(metadata))
                        .build())
                .build();
    }
}
