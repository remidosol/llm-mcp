package com.remidosol.llmmcp.infra.kubernetes.core;

import com.remidosol.llmmcp.infra.kubernetes.Metadata;
import io.cdktn.cdktn.TerraformResourceLifecycle;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import io.cdktn.providers.kubernetes.service.ServiceConfig;
import io.cdktn.providers.kubernetes.service.ServiceMetadata;
import io.cdktn.providers.kubernetes.service.ServiceSpec;
import io.cdktn.providers.kubernetes.service.ServiceSpecPort;
import software.constructs.Construct;

import java.util.List;

/**
 * ClusterIP Service selecting the standard labels; GKE writes NEG/L4 annotations onto Services, so
 * those are ignored to keep every plan clean (port of {@code KubernetesService}).
 */
public class Service extends io.cdktn.providers.kubernetes.service.Service {

    private final String hostname;

    public Service(Construct scope, String id, KubernetesProvider provider, Metadata metadata, int port, String targetPort) {
        super(scope, id, ServiceConfig.builder()
                .provider(provider)
                .lifecycle(TerraformResourceLifecycle.builder().ignoreChanges(List.of(
                        "metadata[0].annotations[\"cloud.google.com/l4-rbs\"]",
                        "metadata[0].annotations[\"cloud.google.com/neg\"]",
                        "metadata[0].annotations[\"cloud.google.com/neg-status\"]")).build())
                .metadata(ServiceMetadata.builder()
                        .name(metadata.name()).namespace(metadata.namespace())
                        .labels(metadata.labels()).annotations(metadata.annotations()).build())
                .spec(ServiceSpec.builder()
                        .selector(metadata.labels())
                        .port(List.of(ServiceSpecPort.builder().name("http").port(port).targetPort(targetPort).build()))
                        .build())
                .build());
        this.hostname = metadata.hostname();
    }

    /** {@code <name>.<namespace>.svc.cluster.local} */
    public String hostname() {
        return hostname;
    }
}
