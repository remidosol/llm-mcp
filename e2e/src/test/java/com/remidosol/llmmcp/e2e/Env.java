package com.remidosol.llmmcp.e2e;

/** Where the running system is; override with environment variables for kind/GKE runs. */
final class Env {

    static final String JOB_URL = System.getenv().getOrDefault("E2E_JOB_URL", "http://localhost:8081");
    static final String CREDIT_URL = System.getenv().getOrDefault("E2E_CREDIT_URL", "http://localhost:8082");
    static final String KAFKA = System.getenv().getOrDefault("E2E_KAFKA", "localhost:9092");
    static final String API_KEY = System.getenv().getOrDefault("E2E_API_KEY", "local-dev-key");
    static final String ADMIN_API_KEY = System.getenv().getOrDefault("E2E_ADMIN_API_KEY", "local-admin-key");

    private Env() {
    }
}
