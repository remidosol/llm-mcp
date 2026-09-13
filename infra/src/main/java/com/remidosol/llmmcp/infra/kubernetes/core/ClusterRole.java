package com.remidosol.llmmcp.infra.kubernetes.core;

import com.remidosol.llmmcp.infra.kubernetes.Metadata;
import io.cdktn.providers.kubernetes.cluster_role.ClusterRoleConfig;
import io.cdktn.providers.kubernetes.cluster_role.ClusterRoleMetadata;
import io.cdktn.providers.kubernetes.cluster_role.ClusterRoleRule;
import io.cdktn.providers.kubernetes.cluster_role_binding.ClusterRoleBindingConfig;
import io.cdktn.providers.kubernetes.cluster_role_binding.ClusterRoleBindingMetadata;
import io.cdktn.providers.kubernetes.cluster_role_binding.ClusterRoleBindingRoleRef;
import io.cdktn.providers.kubernetes.cluster_role_binding.ClusterRoleBindingSubject;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import software.constructs.Construct;

import java.util.List;

/** ClusterRole that binds itself to the given service accounts (port of {@code KubernetesClusterRole} + binding). */
public class ClusterRole extends io.cdktn.providers.kubernetes.cluster_role.ClusterRole {

    public ClusterRole(Construct scope, String id, KubernetesProvider provider, Metadata metadata,
                       List<ClusterRoleRule> rules, List<ServiceAccount> serviceAccounts) {
        super(scope, id, ClusterRoleConfig.builder()
                .provider(provider)
                .metadata(ClusterRoleMetadata.builder().name(metadata.name()).labels(metadata.labels()).build())
                .rule(rules)
                .build());
        if (serviceAccounts != null && !serviceAccounts.isEmpty()) {
            new io.cdktn.providers.kubernetes.cluster_role_binding.ClusterRoleBinding(this, "binding", ClusterRoleBindingConfig.builder()
                    .provider(provider)
                    .metadata(ClusterRoleBindingMetadata.builder().name(metadata.name()).build())
                    .roleRef(ClusterRoleBindingRoleRef.builder().apiGroup("rbac.authorization.k8s.io").kind("ClusterRole").name(metadata.name()).build())
                    .subject(serviceAccounts.stream().map(sa -> ClusterRoleBindingSubject.builder()
                            .kind("ServiceAccount").name(sa.objectMetadata().name()).namespace(sa.objectMetadata().namespace()).build()).toList())
                    .build());
        }
    }
}
