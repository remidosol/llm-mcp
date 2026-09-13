package com.remidosol.llmmcp.infra.kubernetes.construct;

import com.remidosol.llmmcp.infra.kubernetes.Metadata;
import com.remidosol.llmmcp.infra.kubernetes.core.ConfigMap;
import com.remidosol.llmmcp.infra.kubernetes.core.Deployment;
import com.remidosol.llmmcp.infra.kubernetes.core.Manifests;
import com.remidosol.llmmcp.infra.kubernetes.core.Service;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpec;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainer;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerEnv;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerEnvFrom;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerEnvFromConfigMapRef;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerEnvFromSecretRef;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerEnvValueFrom;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerEnvValueFromSecretKeyRef;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerLivenessProbe;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerLivenessProbeHttpGet;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerPort;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerReadinessProbe;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerReadinessProbeHttpGet;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerResources;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerStartupProbe;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerStartupProbeHttpGet;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerVolumeMount;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecVolume;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecVolumeEmptyDir;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import software.constructs.Construct;
import io.cdktn.cdktn.ITerraformDependable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One llm-mcp Spring Boot service on the cluster: ConfigMap (non-secret env) + Deployment
 * (probes, resources, graceful shutdown, secrets as env) + ClusterIP Service + optional CPU HPA.
 * The Java twin of {@code deploy/k8s/base/<service>} — same probes, same resources, same env names —
 * plus the reference's hardening (non-root, read-only rootfs, anti-affinity, topology spread).
 */
public class SpringBootService extends Construct {

    public record Spec(String name, int port, String image, String database, String dbRole, int replicas,
                       Map<String, String> extraEnv, Integer hpaMaxReplicas) {
    }

    private final Deployment deployment;
    private final Service service;
    private final ConfigMap configMap;

    public SpringBootService(Construct scope, String id, KubernetesProvider provider, Metadata metadata, Spec spec,
                             String kafkaBootstrap, String redisHost, String postgresHost, String otelEndpoint,
                             String appSecretsName, List<? extends ITerraformDependable> dependsOn) {
        super(scope, id);

        Map<String, String> env = new LinkedHashMap<>();
        env.put("SPRING_PROFILES_ACTIVE", "k8s,gke");
        env.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://" + postgresHost + ":5432/" + spec.database());
        env.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", kafkaBootstrap);
        env.put("SPRING_DATA_REDIS_HOST", redisHost);
        env.put("SPRING_DATA_REDIS_PORT", "6379");
        env.put("JAVA_TOOL_OPTIONS", "-XX:MaxRAMPercentage=75 -Djava.io.tmpdir=/tmp"); // read-only rootfs: /tmp is a volume
        env.put("OTEL_ENABLED", "true");
        env.put("OTEL_EXPORTER_OTLP_ENDPOINT", otelEndpoint);
        env.putAll(spec.extraEnv());
        this.configMap = new ConfigMap(this, "config", provider, metadata.withName(spec.name() + "-config"), env);

        DeploymentSpecTemplateSpecContainer container = DeploymentSpecTemplateSpecContainer.builder()
                .name(spec.name())
                .image(spec.image())
                .port(List.of(DeploymentSpecTemplateSpecContainerPort.builder().name("http").containerPort(spec.port()).build()))
                .envFrom(List.of(
                        DeploymentSpecTemplateSpecContainerEnvFrom.builder().configMapRef(
                                DeploymentSpecTemplateSpecContainerEnvFromConfigMapRef.builder().name(spec.name() + "-config").build()).build(),
                        DeploymentSpecTemplateSpecContainerEnvFrom.builder().secretRef(
                                DeploymentSpecTemplateSpecContainerEnvFromSecretRef.builder().name(appSecretsName).build()).build()))
                .env(List.of(
                        secretEnv("SPRING_DATASOURCE_USERNAME", "pg-" + spec.dbRole(), "username"),
                        secretEnv("SPRING_DATASOURCE_PASSWORD", "pg-" + spec.dbRole(), "password")))
                .resources(DeploymentSpecTemplateSpecContainerResources.builder()
                        .requests(Map.of("cpu", "500m", "memory", "768Mi"))
                        .limits(Map.of("cpu", "1", "memory", "1Gi"))
                        .build())
                .startupProbe(DeploymentSpecTemplateSpecContainerStartupProbe.builder()
                        .httpGet(DeploymentSpecTemplateSpecContainerStartupProbeHttpGet.builder().path("/actuator/health/liveness").port("http").build())
                        .periodSeconds(2).failureThreshold(30).build())
                .livenessProbe(DeploymentSpecTemplateSpecContainerLivenessProbe.builder()
                        .httpGet(DeploymentSpecTemplateSpecContainerLivenessProbeHttpGet.builder().path("/actuator/health/liveness").port("http").build())
                        .periodSeconds(10).build())
                .readinessProbe(DeploymentSpecTemplateSpecContainerReadinessProbe.builder()
                        .httpGet(DeploymentSpecTemplateSpecContainerReadinessProbeHttpGet.builder().path("/actuator/health/readiness").port("http").build())
                        .periodSeconds(5).failureThreshold(3).build())
                .volumeMount(List.of(DeploymentSpecTemplateSpecContainerVolumeMount.builder().name("tmp").mountPath("/tmp").build()))
                .build();

        DeploymentSpecTemplateSpec podSpec = DeploymentSpecTemplateSpec.builder()
                .container(List.of(container))
                .volume(List.of(DeploymentSpecTemplateSpecVolume.builder().name("tmp")
                        .emptyDir(DeploymentSpecTemplateSpecVolumeEmptyDir.builder().build()).build()))
                .terminationGracePeriodSeconds(30)
                .build();

        this.deployment = new Deployment(this, "deployment", provider, metadata, spec.replicas(), podSpec);
        List<ITerraformDependable> deps = new ArrayList<>(dependsOn);
        deps.add(configMap);
        this.deployment.getNode().addDependency(deps.toArray(new software.constructs.IDependable[0]));
        this.service = new Service(this, "service", provider, metadata, spec.port(), "http");

        if (spec.hpaMaxReplicas() != null) {
            Manifests.of(this, "hpa", provider, hpa(metadata, spec), List.of(deployment));
        }
    }

    private static DeploymentSpecTemplateSpecContainerEnv secretEnv(String name, String secret, String key) {
        return DeploymentSpecTemplateSpecContainerEnv.builder().name(name)
                .valueFrom(DeploymentSpecTemplateSpecContainerEnvValueFrom.builder()
                        .secretKeyRef(DeploymentSpecTemplateSpecContainerEnvValueFromSecretKeyRef.builder().name(secret).key(key).build())
                        .build())
                .build();
    }

    /** autoscaling/v2 HPA on CPU (task 7.4): the request-driven services scale on CPU, the worker on lag (KEDA). */
    private static Map<String, Object> hpa(Metadata metadata, Spec spec) {
        return Map.of(
                "apiVersion", "autoscaling/v2",
                "kind", "HorizontalPodAutoscaler",
                "metadata", Map.of("name", spec.name(), "namespace", metadata.namespace(), "labels", metadata.labels()),
                "spec", Map.of(
                        "scaleTargetRef", Map.of("apiVersion", "apps/v1", "kind", "Deployment", "name", spec.name()),
                        "minReplicas", spec.replicas(),
                        "maxReplicas", spec.hpaMaxReplicas(),
                        "metrics", List.of(Map.of("type", "Resource", "resource", Map.of("name", "cpu",
                                "target", Map.of("type", "Utilization", "averageUtilization", 70))))));
    }

    public Deployment deployment() {
        return deployment;
    }

    public Service service() {
        return service;
    }

}
