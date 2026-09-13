package com.remidosol.llmmcp.infra;

import java.util.Optional;

/**
 * Everything environment-specific comes from the environment, so `cdktn synth` works without a GCP
 * account (placeholders) and `cdktn deploy` gets the real values from the shell that runs it.
 * Mirrors the reference StackConfig: displayName / repoName / slug drive naming and the state prefix.
 *
 * <p>{@code imageTag}: the Artifact Registry tag the main stack resolves to digests — {@code IMAGE_TAG}
 * when set (a PR plans against its base branch), else {@code GITHUB_REF_NAME} (a push to main), else
 * {@code main}. {@code sharedVpc}: an organisation-level Shared VPC when the host project exposes one
 * ({@code INFRA_NETWORK} / {@code INFRA_SUBNETWORK} self-links plus the two secondary range names);
 * without it the cluster lands in the project's default network.
 */
public record InfraConfig(String displayName, String repoName, String slug, String projectId, String region,
                          String githubRepository, String stateBucket, Environment environment,
                          boolean cloudSql, String imageTag, Optional<SharedVpc> sharedVpc) {

    /** Host-project Shared VPC attachment: self-links and the subnet's secondary ranges for pods and services. */
    public record SharedVpc(String network, String subnetwork, String podsRange, String servicesRange) {
    }

    public static InfraConfig fromEnv() {
        String project = env("GCP_PROJECT_ID", "my-gcp-project");
        return new InfraConfig(
                "llm-mcp",
                "llm-mcp",
                "llmmcp",
                project,
                env("GCP_REGION", "europe-west1"),
                env("GITHUB_REPOSITORY", "remidosol/llm-mcp"),
                env("TF_STATE_BUCKET", project + "-llm-mcp-tfstate"),
                Environment.PRODUCTION,
                Boolean.parseBoolean(env("INFRA_CLOUD_SQL", "false")),   // CNPG stays; Cloud SQL is opt-in
                env("IMAGE_TAG", env("GITHUB_REF_NAME", "main")),
                env("INFRA_NETWORK", "").isEmpty() ? Optional.empty() : Optional.of(new SharedVpc(
                        env("INFRA_NETWORK", ""), env("INFRA_SUBNETWORK", ""),
                        env("INFRA_PODS_RANGE", "pods"), env("INFRA_SERVICES_RANGE", "services"))));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
