package com.remidosol.llmmcp.infra.kubernetes.core;

import com.remidosol.llmmcp.infra.kubernetes.Metadata;
import io.cdktn.providers.kubernetes.config_map.ConfigMapConfig;
import io.cdktn.providers.kubernetes.config_map.ConfigMapMetadata;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import software.constructs.Construct;

import java.util.Map;

/** ConfigMap with standardized metadata; Reloader restarts consumers when its data changes. */
public class ConfigMap extends io.cdktn.providers.kubernetes.config_map.ConfigMap {

    public ConfigMap(Construct scope, String id, KubernetesProvider provider, Metadata metadata, Map<String, String> data) {
        super(scope, id, ConfigMapConfig.builder()
                .provider(provider)
                .metadata(ConfigMapMetadata.builder()
                        .name(metadata.name()).namespace(metadata.namespace())
                        .labels(metadata.labels()).annotations(metadata.annotations()).build())
                .data(data)
                .build());
    }
}
