package com.remidosol.llmmcp.infra.stack;

import com.remidosol.llmmcp.infra.InfraConfig;
import com.remidosol.llmmcp.infra.kubernetes.Metadata;
import com.remidosol.llmmcp.infra.kubernetes.construct.KedaScaledObject;
import com.remidosol.llmmcp.infra.kubernetes.construct.OtelLgtm;
import com.remidosol.llmmcp.infra.kubernetes.construct.SpringBootService;
import com.remidosol.llmmcp.infra.kubernetes.core.Manifests;
import com.remidosol.llmmcp.infra.kubernetes.core.Namespace;
import com.remidosol.llmmcp.infra.kubernetes.core.Secret;
import io.cdktn.cdktn.Fn;
import io.cdktn.cdktn.TerraformVariable;
import io.cdktn.providers.google.data_google_artifact_registry_docker_image.DataGoogleArtifactRegistryDockerImage;
import io.cdktn.providers.google.data_google_client_config.DataGoogleClientConfig;
import io.cdktn.providers.helm.provider.HelmProvider;
import io.cdktn.providers.helm.provider.HelmProviderKubernetes;
import io.cdktn.providers.helm.release.Release;
import io.cdktn.providers.helm.release.ReleaseSet;
import io.cdktn.providers.kubernetes.manifest.Manifest;
import io.cdktn.providers.kubernetes.provider.KubernetesProvider;
import software.constructs.Construct;
import io.cdktn.cdktn.ITerraformDependable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything ON the cluster (reference: MainStack): providers wired to the common stack's GKE
 * cluster with a short-lived token, operators via Helm, Kafka/Postgres CRs from the same YAML the
 * kind cluster uses, Secrets from sensitive variables, the three Spring services with CPU HPA on
 * the request-driven ones and a KEDA Kafka-lag ScaledObject on the worker, and otel-lgtm.
 * Images are pushed under the branch tag ({@code :main}) and resolved here to their digest with an
 * Artifact Registry data source, so every apply pins exactly the bytes behind the tag at that moment:
 * a changed digest rolls the Deployment, an unchanged one is a no-op — no tag variables to thread through CI.
 */
public class MainStack extends Stack {

    private static final String KAFKA_BOOTSTRAP = "llm-mcp-kafka-bootstrap.kafka.svc.cluster.local:9092";
    private static final String POSTGRES_HOST = "pg-rw.db.svc.cluster.local";

    public MainStack(Construct scope, String id, InfraConfig config, CommonStack common) {
        super(scope, id, config);
        Path deploy = Path.of("..", "deploy").toAbsolutePath().normalize();

        // --- providers: the GKE endpoint + CA from the common stack, an access token from the caller's credentials
        DataGoogleClientConfig client = DataGoogleClientConfig.Builder.create(this, "client").build();
        String host = "https://" + common.cluster().getEndpoint();
        String ca = Fn.base64decode(common.cluster().getMasterAuth().getClusterCaCertificate());
        KubernetesProvider kubernetes = KubernetesProvider.Builder.create(this, "kubernetes")
                .host(host).token(client.getAccessToken()).clusterCaCertificate(ca).build();
        HelmProvider helm = HelmProvider.Builder.create(this, "helm")
                .kubernetes(HelmProviderKubernetes.builder().host(host).token(client.getAccessToken()).clusterCaCertificate(ca).build())
                .build();

        // --- variables (secrets never live in code)
        TerraformVariable apiKeys = variable("app_api_keys", "comma-separated user API keys");
        TerraformVariable adminApiKeys = variable("app_admin_api_keys", "comma-separated admin API keys");
        TerraformVariable pgPassword = variable("postgres_password", "password of the job/credit/llm database roles");

        // --- namespaces
        Namespace kafkaNs = new Namespace(this, "ns-kafka", kubernetes, "kafka", "llm-mcp");
        Namespace dbNs = new Namespace(this, "ns-db", kubernetes, "db", "llm-mcp");
        Namespace appNs = new Namespace(this, "ns-app", kubernetes, "llm-mcp", "llm-mcp");
        Namespace obsNs = new Namespace(this, "ns-observability", kubernetes, "observability", "llm-mcp");
        Namespace kedaNs = new Namespace(this, "ns-keda", kubernetes, "keda", "llm-mcp");

        // --- operators (Helm): Strimzi, CloudNativePG, KEDA
        Construct operators = new Construct(this, "operators");
        Release strimzi = Release.Builder.create(operators, "strimzi")
                .provider(helm).name("strimzi").namespace(kafkaNs.namespaceName())
                .repository("oci://quay.io/strimzi-helm").chart("strimzi-kafka-operator").version("1.2.0")
                .wait(true).timeout(600).dependsOn(List.of(kafkaNs)).build();
        Release cnpg = Release.Builder.create(operators, "cnpg")
                .provider(helm).name("cnpg").namespace("cnpg-system").createNamespace(true)
                .repository("https://cloudnative-pg.github.io/charts").chart("cloudnative-pg").version("0.29.0")
                .wait(true).timeout(600).build();
        Release keda = Release.Builder.create(operators, "keda")
                .provider(helm).name("keda").namespace(kedaNs.namespaceName())
                .repository("https://kedacore.github.io/charts").chart("keda").version("2.20.2")
                .wait(true).timeout(600).dependsOn(List.of(kedaNs)).build();

        // --- secrets: three copies of the role credentials (CNPG, apps, Debezium) + the API keys
        Construct secrets = new Construct(this, "secrets");
        Map<String, Secret> roleSecrets = new LinkedHashMap<>();
        for (String role : List.of("job", "credit", "llm")) {
            for (Namespace ns : List.of(dbNs, appNs, kafkaNs)) {
                Metadata meta = Metadata.of("pg-" + role, ns.namespaceName(), "llm-mcp", config.environment());
                roleSecrets.put(role + "@" + ns.namespaceName(), new Secret(secrets, "pg-" + role + "-" + ns.namespaceName(), kubernetes, meta,
                        "kubernetes.io/basic-auth", Map.of("username", role, "password", pgPassword.getStringValue())));
            }
        }
        Secret appSecrets = new Secret(secrets, "app-secrets", kubernetes,
                Metadata.of("app-secrets", appNs.namespaceName(), "llm-mcp", config.environment()), "Opaque",
                Map.of("APP_API_KEYS", apiKeys.getStringValue(), "APP_ADMIN_API_KEYS", adminApiKeys.getStringValue()));

        // --- Kafka + Postgres from the SAME manifests kind uses (CRDs must exist at plan time: see Manifests)
        Construct platform = new Construct(this, "platform");
        List<ITerraformDependable> afterStrimzi = List.of(strimzi);
        List<Manifest> kafka = new ArrayList<>(Manifests.fromFile(platform, "kafka", kubernetes, deploy.resolve("kafka/kafka.yaml"), afterStrimzi));
        kafka.addAll(Manifests.fromFile(platform, "topics", kubernetes, deploy.resolve("kafka/topics.yaml"), afterStrimzi));
        kafka.addAll(Manifests.fromFile(platform, "kafka-ui", kubernetes, deploy.resolve("kafka/kafka-ui.yaml"), afterStrimzi));
        List<ITerraformDependable> afterCnpg = new ArrayList<>(List.of(cnpg));
        afterCnpg.addAll(roleSecrets.values());
        List<Manifest> postgres = Manifests.fromFile(platform, "postgres", kubernetes, deploy.resolve("postgres/cluster.yaml"), afterCnpg);

        // --- observability
        Metadata obsMeta = Metadata.of("otel-lgtm", obsNs.namespaceName(), "observability", config.environment());
        OtelLgtm otel = new OtelLgtm(this, "otel-lgtm", kubernetes, obsMeta, deploy.resolve("observability"), Map.of(
                "SCRAPE_JOB_SERVICE", "job-service.llm-mcp.svc.cluster.local:8081",
                "SCRAPE_CREDIT_SERVICE", "credit-service.llm-mcp.svc.cluster.local:8082",
                "SCRAPE_LLM_WORKER", "llm-worker.llm-mcp.svc.cluster.local:8083"));

        // --- redis + the three services
        Manifests.fromFile(this, "redis", kubernetes, deploy.resolve("k8s/base/redis.yaml"), List.of(appNs));
        List<ITerraformDependable> platformReady = new ArrayList<>(kafka);
        platformReady.addAll(postgres);
        platformReady.add(appSecrets);
        Map<String, SpringBootService> services = new LinkedHashMap<>();
        services.put("job-service", service(kubernetes, config, common, "job-service", 8081, "job_db", "job", 2, 5, Map.of(), otel, appNs, platformReady));
        services.put("credit-service", service(kubernetes, config, common, "credit-service", 8082, "credit_db", "credit", 2, 5, Map.of(), otel, appNs, platformReady));
        // the fake provider's [FAIL]/[FLAKY]/[SLOW] prompt hooks stay on: the post-deploy e2e job drives the saga's
        // failure and timeout paths with them, and they only ever affect fake:* models
        services.put("llm-worker", service(kubernetes, config, common, "llm-worker", 8083, "llm_db", "llm", 1, null,
                Map.of("APP_LLM_FAKE_FAILURE_INJECTION", "true"), otel, appNs, platformReady));

        // --- the worker scales on consumer lag, not CPU (KEDA)
        new KedaScaledObject(this, "llm-worker-scaler", kubernetes,
                Metadata.of("llm-worker", appNs.namespaceName(), "llm-mcp", config.environment()), "llm-worker",
                KAFKA_BOOTSTRAP, "llm-worker", "credit.events.v1", 10, 1, 3,
                List.of(keda, services.get("llm-worker").deployment()));

        output("job_service", services.get("job-service").service().hostname());
        output("grafana_port_forward", "kubectl -n observability port-forward svc/otel-lgtm 3000:3000");
    }

    private SpringBootService service(KubernetesProvider kubernetes, InfraConfig config, CommonStack common, String name,
                                      int port, String database, String role, int replicas, Integer hpaMax, Map<String, String> extraEnv,
                                      OtelLgtm otel, Namespace ns, List<ITerraformDependable> dependsOn) {
        Metadata meta = Metadata.of(name, ns.namespaceName(), "llm-mcp", config.environment());
        // <name>:<branch> -> <registry>/<name>@sha256:... (the data source fails the plan if nothing was pushed yet)
        DataGoogleArtifactRegistryDockerImage image = DataGoogleArtifactRegistryDockerImage.Builder.create(this, name + "-image")
                .location(config.region()).repositoryId(common.images().getRepositoryId())
                .imageName(name + ":" + config.imageTag()).build();
        return new SpringBootService(this, name, kubernetes, meta,
                new SpringBootService.Spec(name, port, image.getSelfLink(), database, role, replicas, extraEnv, hpaMax),
                KAFKA_BOOTSTRAP, "redis", POSTGRES_HOST, otel.otlpEndpoint(), "app-secrets", dependsOn);
    }

    private TerraformVariable variable(String name, String description) {
        return TerraformVariable.Builder.create(this, name).type("string").sensitive(true).description(description).build();
    }
}
