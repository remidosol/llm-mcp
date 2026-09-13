package com.remidosol.llmmcp.infra.kubernetes.core;

import com.remidosol.llmmcp.infra.kubernetes.Metadata;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import io.cdktn.providers.kubernetes.secret.SecretConfig;
import io.cdktn.providers.kubernetes.secret.SecretMetadata;
import software.constructs.Construct;

import java.util.Map;

/** Secret whose values come from sensitive Terraform variables — never from the repository. */
public class Secret extends io.cdktn.providers.kubernetes.secret.Secret {

    public Secret(Construct scope, String id, KubernetesProvider provider, Metadata metadata, String type,
                  Map<String, String> data) {
        super(scope, id, SecretConfig.builder()
                .provider(provider)
                .metadata(SecretMetadata.builder()
                        .name(metadata.name()).namespace(metadata.namespace())
                        .labels(metadata.labels()).annotations(metadata.annotations()).build())
                .type(type)
                .data(data)
                .build());
    }
}
