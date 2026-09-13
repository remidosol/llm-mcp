package com.remidosol.llmmcp.infra;

/**
 * Everything environment-specific comes from the environment, so `cdktn synth` works without a GCP
 * account (placeholders) and `cdktn deploy` gets the real values from the shell that runs it.
 * Mirrors the reference StackConfig: displayName / repoName / slug drive naming and the state prefix.
 */
public record InfraConfig(String displayName, String repoName, String slug, String projectId, String region,
                          String githubRepository, String stateBucket, Environment environment,
                          boolean cloudSql) {

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
                Boolean.parseBoolean(env("INFRA_CLOUD_SQL", "false")));  // ADR-0020 keeps CNPG; Cloud SQL is opt-in
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
