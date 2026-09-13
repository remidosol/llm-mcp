package com.remidosol.llmmcp.infra;

import com.remidosol.llmmcp.infra.stack.CommonStack;
import com.remidosol.llmmcp.infra.stack.MainStack;
import io.cdktn.cdktn.App;

/**
 * Two stacks, like the reference project minus its development stack: {@code common} owns the GCP
 * foundation (APIs, registry, cluster, identities, optional Cloud SQL), {@code main} owns everything
 * that runs ON the cluster (operators, Kafka/Postgres CRs, secrets, the three services, autoscaling,
 * observability). Deploy order: common, then main.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        App app = new App();
        InfraConfig config = InfraConfig.fromEnv();
        CommonStack common = new CommonStack(app, "common", config);
        new MainStack(app, "main", config, common);
        app.synth();
    }
}
