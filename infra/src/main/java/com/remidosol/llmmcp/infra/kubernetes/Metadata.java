package com.remidosol.llmmcp.infra.kubernetes;

import com.remidosol.llmmcp.infra.Environment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standardized Kubernetes object metadata (port of the reference {@code metadata.ts}): the
 * {@code app.kubernetes.io} label convention plus the OpenTelemetry resource annotations that the
 * collector/agents map onto {@code service.name}, {@code service.namespace} and the environment.
 */
public record Metadata(String name, String namespace, Map<String, String> labels, Map<String, String> annotations) {

    public static Metadata of(String name, String namespace, String partOf, Environment environment) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app.kubernetes.io/name", name);
        labels.put("app.kubernetes.io/part-of", partOf);
        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("resource.opentelemetry.io/service.name", name);
        annotations.put("resource.opentelemetry.io/service.namespace", partOf);
        annotations.put("resource.opentelemetry.io/deployment.environment.name", environment.value());
        return new Metadata(name, namespace, labels, annotations);
    }

    public Metadata withName(String newName) {
        return new Metadata(newName, namespace, labels, annotations);
    }

    public Metadata withAnnotations(Map<String, String> extra) {
        Map<String, String> merged = new LinkedHashMap<>(annotations);
        merged.putAll(extra);
        return new Metadata(name, namespace, labels, merged);
    }

    public Metadata withLabels(Map<String, String> extra) {
        Map<String, String> merged = new LinkedHashMap<>(labels);
        merged.putAll(extra);
        return new Metadata(name, namespace, merged, annotations);
    }

    /** Cluster-local DNS name of a Service with this metadata. */
    public String hostname() {
        return name + "." + namespace + ".svc.cluster.local";
    }
}
