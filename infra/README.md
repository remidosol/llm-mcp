# infra — CDK Terrain (Java)

Two stacks (a shared foundation and the application), the same split we use in other projects:

| Stack | Owns | Deploy |
|---|---|---|
| `common` | GCP foundation: enabled APIs, Artifact Registry, regional GKE Autopilot cluster (Workload Identity on; attached to the organisation's Shared VPC when `INFRA_NETWORK`/`INFRA_SUBNETWORK` are set), deployer service account + Workload Identity Federation for GitHub Actions, optional Cloud SQL (`INFRA_CLOUD_SQL=true`) | once, by hand (`cdktn deploy common`) |
| `main` | Everything on the cluster: namespaces, operators (Strimzi, CloudNativePG, KEDA via Helm), Kafka/Postgres CRs read from `../deploy/**`, Secrets from variables, the three Spring services, CPU HPA (job/credit), KEDA Kafka-lag ScaledObject (worker), otel-lgtm | by hand the first time (after `buck2 build //:docker` so the `:main` images exist), then by `push.yaml` (`buck2 run //infra:apply@main`) — services are `<name>:<branch>` resolved to digests by an Artifact Registry data source |

## Layout

```
src/main/java/com/remidosol/llmmcp/infra/
  Main.java                     App: CommonStack + MainStack
  InfraConfig.java              displayName/repoName/slug + env-driven project/region/repo/bucket
  Environment.java              development | staging | production (labels + OTel annotations)
  stack/Stack.java              base: GcsBackend(<repo>/<stack>), Google provider, readable logical ids
  stack/CommonStack.java
  stack/MainStack.java
  kubernetes/Metadata.java      app.kubernetes.io labels + resource.opentelemetry.io annotations
  kubernetes/core/              ports of the reference core constructs: Pod (hardening defaults),
                                Deployment, Service, ServiceAccount (Workload Identity), ConfigMap,
                                Secret, Namespace, Role(+Binding), ClusterRole(+Binding), Manifests (YAML -> CRs)
  kubernetes/construct/         llm-mcp constructs: SpringBootService, OtelLgtm, KedaScaledObject
  google/CloudSqlPostgres.java  managed-Postgres alternative to CNPG (opt-in)
```

## Commands

```bash
make infra-synth                                  # renders cdktf.out/stacks/{common,main}/cdk.tf.json — no credentials needed
cd infra && npx -y cdktn-cli@0.24.0 diff main     # needs gcloud auth + the state bucket
# deploy/destroy cost money: only with an explicit OK (see docs/runbook.md)
```

Terraform limitation to know: `kubernetes_manifest` resources need their CRD at plan time, so on a
brand-new cluster the first `cdktn deploy main` may need `--target` on the operators (or a second run).
