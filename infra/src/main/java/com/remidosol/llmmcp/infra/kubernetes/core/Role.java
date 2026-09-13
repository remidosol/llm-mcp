package com.remidosol.llmmcp.infra.kubernetes.core;

import com.remidosol.llmmcp.infra.kubernetes.Metadata;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import io.cdktn.providers.kubernetes.role.RoleConfig;
import io.cdktn.providers.kubernetes.role.RoleMetadata;
import io.cdktn.providers.kubernetes.role.RoleRule;
import io.cdktn.providers.kubernetes.role_binding.RoleBindingConfig;
import io.cdktn.providers.kubernetes.role_binding.RoleBindingMetadata;
import io.cdktn.providers.kubernetes.role_binding.RoleBindingRoleRef;
import io.cdktn.providers.kubernetes.role_binding.RoleBindingSubject;
import software.constructs.Construct;

import java.util.List;

/** Namespaced Role that binds itself to the given service accounts (port of {@code KubernetesRole} + binding). */
public class Role extends io.cdktn.providers.kubernetes.role.Role {

    public Role(Construct scope, String id, KubernetesProvider provider, Metadata metadata, List<RoleRule> rules,
                List<ServiceAccount> serviceAccounts) {
        super(scope, id, RoleConfig.builder()
                .provider(provider)
                .metadata(RoleMetadata.builder().name(metadata.name()).namespace(metadata.namespace())
                        .labels(metadata.labels()).build())
                .rule(rules)
                .build());
        if (serviceAccounts != null && !serviceAccounts.isEmpty()) {
            new io.cdktn.providers.kubernetes.role_binding.RoleBinding(this, "binding", RoleBindingConfig.builder()
                    .provider(provider)
                    .metadata(RoleBindingMetadata.builder().name(metadata.name()).namespace(metadata.namespace()).build())
                    .roleRef(RoleBindingRoleRef.builder().apiGroup("rbac.authorization.k8s.io").kind("Role").name(metadata.name()).build())
                    .subject(serviceAccounts.stream().map(sa -> RoleBindingSubject.builder()
                            .kind("ServiceAccount").name(sa.objectMetadata().name()).namespace(sa.objectMetadata().namespace()).build()).toList())
                    .build());
        }
    }
}
