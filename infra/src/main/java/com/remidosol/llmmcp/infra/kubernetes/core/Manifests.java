package com.remidosol.llmmcp.infra.kubernetes.core;

import io.cdktn.providers.kubernetes.manifest.Manifest;
import io.cdktn.providers.kubernetes.manifest.ManifestConfig;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import org.yaml.snakeyaml.Yaml;
import software.constructs.Construct;
import io.cdktn.cdktn.ITerraformDependable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom resources (Strimzi, CloudNativePG, KEDA, HPA…) straight from the YAML files under
 * {@code deploy/}: the kind cluster and GKE share one source of truth, Terraform only adds state and
 * ordering. Note the Terraform limitation: {@code kubernetes_manifest} needs the CRD to exist at PLAN
 * time, so on a brand-new cluster {@code cdktn deploy main} runs twice (operators first).
 */
public final class Manifests {

    private Manifests() {
    }

    /** Every document of a (multi-document) YAML file as one kubernetes_manifest resource. */
    public static List<Manifest> fromFile(Construct scope, String idPrefix, KubernetesProvider provider, Path file,
                                          List<? extends ITerraformDependable> dependsOn) {
        List<Manifest> created = new ArrayList<>();
        int index = 0;
        for (Object document : new Yaml().loadAll(read(file))) {
            if (!(document instanceof Map<?, ?> map) || map.isEmpty()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> manifest = (Map<String, Object>) map;
            Map<?, ?> metadata = (Map<?, ?>) manifest.get("metadata");
            String name = manifest.get("kind") + "-" + (metadata == null ? index : metadata.get("name"));
            created.add(new Manifest(scope, idPrefix + "-" + name.toLowerCase(), ManifestConfig.builder()
                    .provider(provider)
                    .manifest(manifest)
                    .dependsOn(dependsOn)
                    .build()));
            index++;
        }
        return created;
    }

    /** One inline custom resource (used for HPA and KEDA ScaledObjects built in code). */
    public static Manifest of(Construct scope, String id, KubernetesProvider provider, Map<String, Object> manifest,
                              List<? extends ITerraformDependable> dependsOn) {
        return new Manifest(scope, id, ManifestConfig.builder().provider(provider).manifest(manifest).dependsOn(dependsOn).build());
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read manifest " + file, e);
        }
    }
}
