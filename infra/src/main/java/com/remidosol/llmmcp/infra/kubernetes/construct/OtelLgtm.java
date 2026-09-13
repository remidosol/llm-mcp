package com.remidosol.llmmcp.infra.kubernetes.construct;

import com.remidosol.llmmcp.infra.kubernetes.Metadata;
import com.remidosol.llmmcp.infra.kubernetes.core.ConfigMap;
import com.remidosol.llmmcp.infra.kubernetes.core.Deployment;
import com.remidosol.llmmcp.infra.kubernetes.core.Service;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpec;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecAffinity;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainer;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerEnv;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerPort;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerReadinessProbe;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerReadinessProbeHttpGet;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerResources;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerSecurityContext;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecContainerVolumeMount;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecSecurityContext;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecVolume;
import io.cdktn.providers.kubernetes.deployment.DeploymentSpecTemplateSpecVolumeConfigMap;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import software.constructs.Construct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The observability backend (ADR-0024) as a construct — the llm-mcp counterpart of the reference's
 * OpenTelemetry collector construct: one otel-lgtm pod, the collector config and the Grafana
 * dashboard read from {@code deploy/observability} (same files the kind manifest embeds).
 * otel-lgtm runs several processes as root and writes under /otel-lgtm, so the pod-level hardening
 * defaults are relaxed here on purpose; the annotations still mark it for the OTel resource mapping.
 */
public class OtelLgtm extends Construct {

    public static final String IMAGE = "grafana/otel-lgtm:0.33.0";

    private final Service service;

    public OtelLgtm(Construct scope, String id, KubernetesProvider provider, Metadata metadata, Path observabilityDir,
                    Map<String, String> scrapeTargets) {
        super(scope, id);
        ConfigMap config = new ConfigMap(this, "config", provider, metadata.withName("otel-lgtm-config"), Map.of(
                "otelcol-config.yaml", read(observabilityDir.resolve("otelcol-config.yaml")),
                "provider.yaml", read(observabilityDir.resolve("dashboards/provider.yaml"))));
        ConfigMap dashboards = new ConfigMap(this, "dashboards", provider, metadata.withName("otel-lgtm-dashboards"), Map.of(
                "llm-mcp-saga.json", read(observabilityDir.resolve("dashboards/llm-mcp-saga.json"))));

        DeploymentSpecTemplateSpecContainer container = DeploymentSpecTemplateSpecContainer.builder()
                .name("otel-lgtm")
                .image(IMAGE)
                .env(scrapeTargets.entrySet().stream().map(e -> DeploymentSpecTemplateSpecContainerEnv.builder()
                        .name(e.getKey()).value(e.getValue()).build()).toList())
                .port(List.of(
                        DeploymentSpecTemplateSpecContainerPort.builder().name("grafana").containerPort(3000).build(),
                        DeploymentSpecTemplateSpecContainerPort.builder().name("otlp-grpc").containerPort(4317).build(),
                        DeploymentSpecTemplateSpecContainerPort.builder().name("otlp-http").containerPort(4318).build()))
                .volumeMount(List.of(
                        DeploymentSpecTemplateSpecContainerVolumeMount.builder().name("config").mountPath("/otel-lgtm/otelcol-config.yaml").subPath("otelcol-config.yaml").build(),
                        DeploymentSpecTemplateSpecContainerVolumeMount.builder().name("config").mountPath("/otel-lgtm/grafana/conf/provisioning/dashboards/llm-mcp.yaml").subPath("provider.yaml").build(),
                        DeploymentSpecTemplateSpecContainerVolumeMount.builder().name("dashboards").mountPath("/otel-lgtm/dashboards-llm-mcp").build()))
                .resources(DeploymentSpecTemplateSpecContainerResources.builder()
                        .requests(Map.of("cpu", "250m", "memory", "768Mi"))
                        .limits(Map.of("memory", "1536Mi")).build())
                .readinessProbe(DeploymentSpecTemplateSpecContainerReadinessProbe.builder()
                        .httpGet(DeploymentSpecTemplateSpecContainerReadinessProbeHttpGet.builder().path("/api/health").port("grafana").build())
                        .periodSeconds(10).failureThreshold(30).build())
                .securityContext(DeploymentSpecTemplateSpecContainerSecurityContext.builder()
                        .allowPrivilegeEscalation(false).readOnlyRootFilesystem(false).build())
                .build();

        DeploymentSpecTemplateSpec podSpec = DeploymentSpecTemplateSpec.builder()
                .container(List.of(container))
                .volume(List.of(
                        DeploymentSpecTemplateSpecVolume.builder().name("config")
                                .configMap(DeploymentSpecTemplateSpecVolumeConfigMap.builder().name("otel-lgtm-config").build()).build(),
                        DeploymentSpecTemplateSpecVolume.builder().name("dashboards")
                                .configMap(DeploymentSpecTemplateSpecVolumeConfigMap.builder().name("otel-lgtm-dashboards").build()).build()))
                .securityContext(DeploymentSpecTemplateSpecSecurityContext.builder().runAsUser("0").build())
                .affinity(DeploymentSpecTemplateSpecAffinity.builder().build())   // single replica: no anti-affinity needed
                .build();

        Deployment deployment = new Deployment(this, "deployment", provider, metadata, 1, podSpec);
        deployment.getNode().addDependency(config, dashboards);
        this.service = new Service(this, "service", provider, metadata, 4318, "otlp-http");
    }

    /** OTLP/HTTP endpoint the services push to. */
    public String otlpEndpoint() {
        return "http://" + service.hostname() + ":4318";
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }
}
