package com.remidosol.llmmcp.infra.kubernetes.core;

import com.remidosol.llmmcp.infra.kubernetes.Metadata;
import io.cdktn.providers.google.service_account_iam_member.ServiceAccountIamMember;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import io.cdktn.providers.kubernetes.service_account.ServiceAccountConfig;
import io.cdktn.providers.kubernetes.service_account.ServiceAccountMetadata;
import software.constructs.Construct;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kubernetes ServiceAccount, optionally bound to a Google service account through GKE Workload
 * Identity (port of {@code KubernetesServiceAccount}): the annotation tells GKE which GSA to
 * impersonate, the IAM member lets this KSA do so — no key file in any pod.
 */
public class ServiceAccount extends io.cdktn.providers.kubernetes.service_account.ServiceAccount {

    private final Metadata metadata;

    public ServiceAccount(Construct scope, String id, KubernetesProvider provider, Metadata metadata) {
        this(scope, id, provider, metadata, null, null);
    }

    public ServiceAccount(Construct scope, String id, KubernetesProvider provider, Metadata metadata,
                          String googleServiceAccountEmail, String googleProjectId) {
        super(scope, id, ServiceAccountConfig.builder()
                .provider(provider)
                .metadata(ServiceAccountMetadata.builder()
                        .name(metadata.name()).namespace(metadata.namespace())
                        .labels(metadata.labels()).annotations(annotations(metadata, googleServiceAccountEmail)).build())
                .build());
        this.metadata = metadata;
        if (googleServiceAccountEmail != null) {
            ServiceAccountIamMember.Builder.create(this, "workload-identity")
                    .serviceAccountId("projects/" + googleProjectId + "/serviceAccounts/" + googleServiceAccountEmail)
                    .role("roles/iam.workloadIdentityUser")
                    .member("serviceAccount:" + googleProjectId + ".svc.id.goog[" + metadata.namespace() + "/" + metadata.name() + "]")
                    .build();
        }
    }

    private static Map<String, String> annotations(Metadata metadata, String gsa) {
        Map<String, String> annotations = new LinkedHashMap<>(metadata.annotations());
        if (gsa != null) {
            annotations.put("iam.gke.io/gcp-service-account", gsa);
        }
        return annotations;
    }

    public Metadata objectMetadata() {
        return metadata;
    }
}
