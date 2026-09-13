package com.remidosol.llmmcp.infra.kubernetes.core;

import io.cdktn.providers.kubernetes.namespace.NamespaceConfig;
import io.cdktn.providers.kubernetes.namespace.NamespaceMetadata;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import software.constructs.Construct;

import java.util.Map;

/** Namespace labelled with the part-of it hosts; the name is also exposed for the metadata of its objects. */
public class Namespace extends io.cdktn.providers.kubernetes.namespace.Namespace {

    private final String name;

    public Namespace(Construct scope, String id, KubernetesProvider provider, String name, String partOf) {
        super(scope, id, NamespaceConfig.builder()
                .provider(provider)
                .metadata(NamespaceMetadata.builder().name(name).labels(Map.of("app.kubernetes.io/part-of", partOf)).build())
                .build());
        this.name = name;
    }

    public String namespaceName() {
        return name;
    }
}
