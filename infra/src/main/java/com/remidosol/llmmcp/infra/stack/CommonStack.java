package com.remidosol.llmmcp.infra.stack;

import com.remidosol.llmmcp.infra.InfraConfig;
import com.remidosol.llmmcp.infra.google.CloudSqlPostgres;
import io.cdktn.cdktn.TerraformVariable;
import io.cdktn.providers.google.artifact_registry_repository.ArtifactRegistryRepository;
import io.cdktn.providers.google.container_cluster.ContainerCluster;
import io.cdktn.providers.google.container_cluster.ContainerClusterIpAllocationPolicy;
import io.cdktn.providers.google.container_cluster.ContainerClusterReleaseChannel;
import io.cdktn.providers.google.container_cluster.ContainerClusterWorkloadIdentityConfig;
import io.cdktn.providers.google.data_google_project.DataGoogleProject;
import io.cdktn.providers.google.iam_workload_identity_pool.IamWorkloadIdentityPool;
import io.cdktn.providers.google.iam_workload_identity_pool_provider.IamWorkloadIdentityPoolProvider;
import io.cdktn.providers.google.iam_workload_identity_pool_provider.IamWorkloadIdentityPoolProviderOidc;
import io.cdktn.providers.google.project_iam_member.ProjectIamMember;
import io.cdktn.providers.google.project_service.ProjectService;
import io.cdktn.providers.google.provider.GoogleProvider;
import io.cdktn.providers.google.service_account.ServiceAccount;
import io.cdktn.providers.google.service_account_iam_member.ServiceAccountIamMember;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

/**
 * The GCP foundation (reference: CommonStack): enabled APIs, Artifact Registry, the regional GKE
 * Autopilot cluster, the deployer identity GitHub Actions assumes via Workload Identity Federation
 * (ADR-0023 — no keys), and optionally Cloud SQL. Everything the main stack needs is exposed as
 * getters (cross-stack references become remote-state lookups in the synthesized Terraform).
 */
public class CommonStack extends Stack {

    private static final List<String> APIS = List.of(
            "container.googleapis.com", "artifactregistry.googleapis.com", "iam.googleapis.com",
            "iamcredentials.googleapis.com", "sts.googleapis.com", "compute.googleapis.com",
            "monitoring.googleapis.com", "logging.googleapis.com", "sqladmin.googleapis.com");

    private final ContainerCluster cluster;
    private final ArtifactRegistryRepository images;
    private final ServiceAccount deployer;
    private final IamWorkloadIdentityPoolProvider githubProvider;

    public CommonStack(Construct scope, String id, InfraConfig config) {
        super(scope, id, config);

        DataGoogleProject project = DataGoogleProject.Builder.create(this, "project").build();

        Construct apis = new Construct(this, "apis");
        List<ProjectService> services = APIS.stream()
                .map(api -> ProjectService.Builder.create(apis, api.split("\\.")[0])
                        .service(api).disableOnDestroy(false).build())
                .toList();

        Construct registry = new Construct(this, "registry");
        this.images = ArtifactRegistryRepository.Builder.create(registry, "docker")
                .repositoryId(config.slug())
                .location(config.region())
                .format("DOCKER")
                .description(config.displayName() + " service images (pushed by buck2 //services/<s>:docker under the branch tag)")
                .dependsOn(services)
                .build();

        Construct gke = new Construct(this, "gke");
        ContainerCluster.Builder clusterBuilder = ContainerCluster.Builder.create(gke, "cluster")
                .name(config.slug())
                .location(config.region())               // regional Autopilot: control plane + nodes across zones
                .enableAutopilot(true)
                .deletionProtection(false)               // a demo; `cdktn destroy` must work
                .releaseChannel(ContainerClusterReleaseChannel.builder().channel("REGULAR").build())
                .workloadIdentityConfig(ContainerClusterWorkloadIdentityConfig.builder()
                        .workloadPool(config.projectId() + ".svc.id.goog").build())          // KSA -> GSA without keys
                .dependsOn(services);
        // VPC-native either way (Autopilot requirement): GKE-managed ranges in the default network, or the
        // organisation's Shared VPC with the host project's subnet and its named secondary ranges
        config.sharedVpc().ifPresentOrElse(
                vpc -> clusterBuilder.network(vpc.network()).subnetwork(vpc.subnetwork())
                        .ipAllocationPolicy(ContainerClusterIpAllocationPolicy.builder()
                                .clusterSecondaryRangeName(vpc.podsRange()).servicesSecondaryRangeName(vpc.servicesRange()).build()),
                () -> clusterBuilder.ipAllocationPolicy(ContainerClusterIpAllocationPolicy.builder().build()));
        this.cluster = clusterBuilder.build();

        Construct identity = new Construct(this, "identity");
        this.deployer = ServiceAccount.Builder.create(identity, "deployer")
                .accountId("github-deployer")
                .displayName("GitHub Actions deployer (" + config.displayName() + ")")
                .build();
        for (String role : List.of("roles/container.developer", "roles/artifactregistry.writer")) {
            ProjectIamMember.Builder.create(identity, "deployer-" + role.substring(role.indexOf('/') + 1).replace('.', '-'))
                    .project(config.projectId()).role(role).member("serviceAccount:" + deployer.getEmail()).build();
        }
        IamWorkloadIdentityPool pool = IamWorkloadIdentityPool.Builder.create(identity, "github-pool")
                .workloadIdentityPoolId("github").displayName("GitHub Actions").build();
        this.githubProvider = IamWorkloadIdentityPoolProvider.Builder.create(identity, "github-provider")
                .workloadIdentityPoolId(pool.getWorkloadIdentityPoolId())
                .workloadIdentityPoolProviderId("github-oidc")
                .displayName("GitHub OIDC")
                .attributeMapping(Map.of(
                        "google.subject", "assertion.sub",
                        "attribute.repository", "assertion.repository",
                        "attribute.ref", "assertion.ref"))
                .attributeCondition("assertion.repository == \"" + config.githubRepository() + "\"")   // only this repo
                .oidc(IamWorkloadIdentityPoolProviderOidc.builder().issuerUri("https://token.actions.githubusercontent.com").build())
                .build();
        ServiceAccountIamMember.Builder.create(identity, "deployer-wif-user")
                .serviceAccountId(deployer.getName())
                .role("roles/iam.workloadIdentityUser")
                .member("principalSet://iam.googleapis.com/projects/" + project.getNumber()
                        + "/locations/global/workloadIdentityPools/" + pool.getWorkloadIdentityPoolId()
                        + "/attribute.repository/" + config.githubRepository())
                .build();

        if (config.cloudSql()) {
            TerraformVariable pgPassword = TerraformVariable.Builder.create(this, "postgres_password")
                    .type("string").sensitive(true).description("password for the job/credit/llm Cloud SQL users").build();
            new CloudSqlPostgres(this, "cloud-sql", config.region(), List.of("job", "credit", "llm"),
                    Map.of("job", pgPassword.getStringValue(), "credit", pgPassword.getStringValue(), "llm", pgPassword.getStringValue()));
        }

        output("cluster_name", cluster.getName());
        output("cluster_location", cluster.getLocation());
        output("artifact_registry", registryUrl());
        output("deployer_service_account", deployer.getEmail());
        output("workload_identity_provider", githubProvider.getName());
    }

    public ContainerCluster cluster() {
        return cluster;
    }

    public GoogleProvider googleProvider() {
        return google;
    }

    public ServiceAccount deployer() {
        return deployer;
    }

    public ArtifactRegistryRepository images() {
        return images;
    }

    /** {@code <region>-docker.pkg.dev/<project>/<repo>} */
    public String registryUrl() {
        return config.region() + "-docker.pkg.dev/" + config.projectId() + "/" + images.getRepositoryId();
    }
}
