# Runbook

How to run, inspect, reset and demo. Grows with each phase; Phase 1 scope below.

## Run (local)

```bash
make compose-up          # kafka(+init topics) :9092, kafka-ui :8090, postgres :5433, redis :6379
make run-job             # job-service :8081 (profile: local; JAVA_HOME resolved to JDK 21 by make)
```

From IntelliJ: run `JobServiceApplication` with active profile `local` — Boot's Docker Compose
support (`start-only`) brings the shared infra up and leaves it running.

## Run everything (Phase 4)

```bash
make run-all      # job-service :8081, credit-service :8082, llm-worker :8083 in the background (.run/*.log)
make smoke        # happy job + [FAIL] job for u1, prints statuses, credits and the Kafka trail
make e2e          # black-box saga tests incl. [SLOW] timeout/late result and out-of-order injection (~3 min)
make stop-all
```

Local-profile knobs that make demos fast (production defaults in parentheses): job timeout 20 s (120 s),
watchdog every 5 s (30 s), fake `[SLOW]` delay 30 s (150 s). Chaos prompts (fake provider only):
`[FAIL]` → FAILED + release, `[FLAKY]` → retryable failures on attempts 1–2 (retries arrive in Phase 5),
`[SLOW]` → TIMED_OUT + release, then a late result (`job_result.late = true`, `saga_late_result_total`).
Replay exercise: run llm-worker with `SPRING_KAFKA_CONSUMER_GROUP_ID=llm-worker-replay` — Kafka
re-delivers the whole topic, the inbox (keyed by the logical group `llm-worker`) skips every event.

## Smoke (Phase 1)

```bash
curl -s -X POST localhost:8081/api/jobs \
  -H 'X-User-Id: u1' -H 'Content-Type: application/json' \
  -d '{"prompt":"hello","model":"fake:demo"}'
# → 202 {"jobId": "...", "status": "CREATED", ...}

curl -s localhost:8081/api/jobs/<jobId>   # run twice; second hit is served from Redis
# proof: TRACE logs from org.springframework.cache, or /actuator/metrics/cache.gets
```

- OpenAPI UI: <http://localhost:8081/swagger-ui.html>
- Kafka UI: <http://localhost:8090> — 6 topics (3 × `.events.v1` + 3 × `.DLT`) must exist
- Health: `curl localhost:8081/actuator/health/readiness`

## Kafka (Phase 2)

Event trail for one job (replace `<jobId>`):

```bash
# what job-service said
docker exec llm-mcp-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:19092 \
  --topic job.events.v1 --from-beginning --property print.key=true --property print.headers=true --timeout-ms 3000 | grep <jobId>
# what credit-service answered
docker exec llm-mcp-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:19092 \
  --topic credit.events.v1 --from-beginning --property print.key=true --property print.headers=true --timeout-ms 3000 | grep <jobId>
# dead letters
docker exec llm-mcp-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:19092 \
  --topic job.events.v1.DLT --from-beginning --property print.headers=true --timeout-ms 3000
# consumer group lag
docker exec llm-mcp-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:19092 --describe --group credit-service
```

kafka-ui (<http://localhost:8090>) walkthrough: **Topics → job.events.v1 → Messages** shows key
(= jobId), headers (`eventType`, `eventId`) and the JSON envelope; **Consumers → credit-service**
shows per-partition lag (should be 0 when idle); **Topics → job.events.v1.DLT** holds poison
records with the original headers plus `kafka_dlt-exception-message` etc. added by the recoverer.
Metrics: `inbox_duplicate_total`, `dlt_messages_total{topic}` and the client's
`kafka_consumer_fetch_manager_records_lag` on `/actuator/prometheus` of credit-service (:8082).

## Outbox and CDC (Phase 3)

```bash
# pending outbox rows (polling mode drains them within ~0.5 s)
docker exec llm-mcp-postgres-1 psql -U llm -d job_db -c "select type, aggregateid, created_at, published_at from outbox order by id desc limit 5"
curl -s localhost:8081/actuator/prometheus | grep -E '^outbox_(pending|publish_latency_seconds_(count|sum))'

# CDC mode: Debezium Connect + connectors, services with the poller disabled
make compose-up-cdc                      # adds debezium-connect on :8093 (profile cdc)
make debezium-register                   # PUT deploy/debezium/*-connector.json → expect "state":"RUNNING"
APP_OUTBOX_PUBLISHER=cdc make run-job    # and run-credit likewise
curl -s localhost:8093/connectors/job-outbox-connector/status
docker exec llm-mcp-postgres-1 psql -U llm -d job_db -c "select slot_name, active, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)) as retained_wal from pg_replication_slots"
```

Switching modes: stop the services, change `APP_OUTBOX_PUBLISHER`, start again. Rows created while
the poller was on and already published are not re-emitted by Debezium (`snapshot.mode=no_data`);
rows created in CDC mode never get `published_at` (retention deletes them by age).

## MCP, security, resilience (Phase 5)

```bash
# API keys: every /api call needs X-API-Key (APP_API_KEYS); topup needs an admin key (APP_ADMIN_API_KEYS)
curl -s -H 'X-API-Key: local-dev-key' localhost:8082/api/credits/u1
curl -s -X POST -H 'X-API-Key: local-admin-key' -H 'Content-Type: application/json' \
  -d '{"amount":100}' localhost:8082/api/credits/u1/topup
# missing/invalid key -> 401 problem+json, user key on topup -> 403

# MCP server (stateless Streamable HTTP on job-service; open without a key in the local profile)
claude mcp add --transport http job-service http://localhost:8081/mcp        # or rely on .mcp.json (project scope)
npx @modelcontextprotocol/inspector --cli http://localhost:8081/mcp --transport http --method tools/list
npx @modelcontextprotocol/inspector --cli http://localhost:8081/mcp --transport http \
  --method tools/call --tool-name create_job --tool-arg userId=u1 --tool-arg prompt=hello --tool-arg model=fake:demo
npx @modelcontextprotocol/inspector --cli http://localhost:8081/mcp --transport http \
  --method resources/read --uri "job://<jobId>/result"
make mcp-check            # scripted version of the four calls above (create -> poll -> read result -> prompt)
# raw JSON-RPC (what the clients do underneath)
curl -s localhost:8081/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# resilience: breaker state + retry/ratelimit metrics (llm-worker)
curl -s -H 'X-API-Key: local-admin-key' localhost:8083/actuator/health | jq '.components.circuitBreakers'
curl -s localhost:8083/actuator/prometheus | grep -E 'resilience4j_circuitbreaker_state|resilience4j_retry_calls|ratelimit_rejected'
# readiness includes db + redis + kafka; liveness does not
curl -s localhost:8081/actuator/health/readiness | jq
```

## Kubernetes on kind (Phase 6)

```bash
make kind-up            # kind cluster llm-mcp: control-plane + worker, host :30080 -> job-service NodePort
make kind-load          # Jib -> local Docker daemon (arm64) + Debezium Connect image, then kind load
make deploy-local       # Strimzi (Helm, ns kafka) + CNPG (manifest, ns cnpg-system) + Kafka/topics + Postgres/roles/databases
                        #   + Secrets from .env (scripts/k8s-secrets.sh) + kubectl apply -k deploy/k8s/overlays/local
make smoke-k8s          # smoke through port-forwards; event trail read inside the broker pod
make deploy-cdc         # stretch: KafkaConnect + 3 KafkaConnectors, services switched to APP_OUTBOX_PUBLISHER=cdc
make pg-forward         # Postgres at localhost:5433 (pgAdmin/psql): db job_db user job, credit_db/credit, llm_db/llm, password = POSTGRES_PASSWORD
make kafka-ui           # port-forward Kafka UI to http://localhost:8090 (topics, messages, consumer groups)
make k8s-status | k8s-reset | kind-down
# kind has no UI of its own: `kubectl get pods -A` (everything lives in kafka/db/llm-mcp, NOT default),
# or k9s (`brew install k9s`), Headlamp/OpenLens desktop apps. Docker Desktop's Kubernetes tab only
# shows its own built-in cluster; the kind nodes appear under Containers as llm-mcp-control-plane/worker.

# inspect
kubectl get pods -A
kubectl -n llm-mcp describe pod -l app=job-service | sed -n '/Events/,$p'     # probe failures, image pull, OOMKilled
kubectl -n llm-mcp logs deploy/llm-worker -f --tail=100
kubectl -n kafka get kafka,kafkanodepool,kafkatopic                          # Strimzi reconciliation status
kubectl -n kafka exec llm-mcp-dual-role-0 -- /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups
kubectl -n db get cluster,database                                            # CNPG: instances, primary, Database applied=true
kubectl -n db exec pg-1 -- psql -U postgres -c '\l'                           # three databases, three owners
kubectl -n kafka get kafkaconnector -o wide                                    # connector/task state (deploy-cdc)

# reach the services from the laptop
curl -s -H 'X-API-Key: local-dev-key' localhost:30080/api/jobs?limit=1 -H 'X-User-Id: u1'   # NodePort
kubectl -n llm-mcp port-forward svc/credit-service 8082:8082                                   # anything else
# MCP from Claude Code against the cluster: port-forward 8081 then `claude mcp add --transport http job-service-k8s http://localhost:8081/mcp --header "X-API-Key: local-dev-key"`

# chaos
kubectl -n llm-mcp delete pod -l app=llm-worker        # mid-job: inbox claim rolls back, redelivery on the new pod
kubectl -n llm-mcp scale deploy/llm-worker --replicas=2 # watch the consumer group rebalance (3 partitions -> 2+1)
kubectl -n llm-mcp patch deploy/job-service --type=json -p='[{"op":"replace","path":"/spec/template/spec/containers/0/readinessProbe/httpGet/path","value":"/nope"}]'
#   -> new pod never Ready, rollout stalls, old pod keeps serving (readiness gates traffic, not the rollout's old pod)
kubectl -n llm-mcp rollout undo deploy/job-service
```

## Buck2 (task runner) and target determination

Gotchas: `tools/buck2` is in `[project] ignore`, so the daemon does not watch edits to the `.bzl` rules —
after changing them run `buck2 kill` (or the next command shows the OLD target definitions). `:docker`
targets need a real registry and credentials (`gcloud auth configure-docker`); locally use `:image`.

```bash
mise install                                    # Temurin 21, Node, buck2, buildifier, git-cliff, opentofu (mise.toml)
buck2 targets //...                             # the graph
buck2 build //contracts:jar                     # Maven inside a sandbox copy of contracts/ + root pom + wrapper
buck2 build //services/job-service:verify       # tests (Testcontainers); output = surefire reports
buck2 build //services/job-service:image        # Jib -> local daemon as llm-mcp/job-service:local
buck2 build //services/job-service:docker -c llmmcp.registry=europe-west1-docker.pkg.dev/PROJECT/llmmcp -c llmmcp.tag=abc1234   # push (needs gcloud auth configure-docker)
buck2 build //infra:synth                       # cdktf.out for both stacks (no credentials)
buck2 run //infra:plan@main                     # OpenTofu plan (needs the state bucket) — apply only with an explicit OK
scripts/changed-targets.sh <base-sha> <head-sha> # what a diff affects: {"verify":[…],"docker":[…],"infra":bool}
buck2 uquery "rdeps(//..., owner('contracts/src/main/java/com/remidosol/llmmcp/contracts/EventEnvelope.java'))"
```
Notes: rules live in `tools/buck2/rules/` (`tools/buck2` is the prelude cell; `command`, `files`, `group` + macros); `buck2 uquery --console none` prints nothing — use `--console simple`; changing any BUCK/.bzl file
means "rebuild everything" for CI; `buck-out/` and `target/` are ignored by the graph.

## Observability (Phase 7)

```bash
# kind
make deploy-observability     # otel-lgtm in ns observability (services already point at it via ConfigMap)
make grafana                  # http://localhost:3000 — Dashboards: "llm-mcp — saga, outbox, LLM, resilience"; Explore: Tempo / Loki
# compose
make compose-up-observability && OTEL_ENABLED=true make run-all
# find one job's trace (Tempo search via Grafana's datasource proxy)
curl -s 'localhost:3000/api/datasources/proxy/uid/tempo/api/search?tags=service.name%3Djob-service&limit=5' | jq '.traces[] | {traceID, rootServiceName, durationMs}'
# every log line carries [service,traceId,spanId,jobId] — grep a jobId in Loki, click the trace_id to jump to Tempo
```

## GCP / GKE (Phase 7 — NOT executed; costs money, needs an explicit OK)

```bash
# 0. one-time, by hand (a backend cannot create its own bucket)
gcloud auth login && gcloud config set project $GCP_PROJECT_ID
gsutil mb -l europe-west1 gs://$GCP_PROJECT_ID-llm-mcp-tfstate && gsutil versioning set on gs://$GCP_PROJECT_ID-llm-mcp-tfstate
gcloud billing budgets create --billing-account=… --display-name=llm-mcp --budget-amount=20USD   # cost control
# 1. infra (CDKTN, Java): synth is free, deploy is not
make infra-synth                                            # renders infra/cdktf.out/stacks/{common,main}/cdk.tf.json
cd infra && export GCP_PROJECT_ID=… GCP_REGION=europe-west1 GITHUB_REPOSITORY=remidosol/llm-mcp
npx -y cdktn-cli@0.24.0 deploy common                        # <- OK required: APIs, registry, GKE Autopilot, deployer SA + WIF
# outputs -> GitHub: secrets GCP_PROJECT_ID, GCP_WIF_PROVIDER (workload_identity_provider), GCP_DEPLOYER_SA (deployer_service_account),
#            APP_API_KEYS, APP_ADMIN_API_KEYS, POSTGRES_PASSWORD, E2E_API_KEY, E2E_ADMIN_API_KEY; vars GCP_REGION;
#            environment "gke" with required reviewers (create it BEFORE the first push, otherwise Actions auto-creates it without a gate).
#            Organisation-level Shared VPC: INFRA_NETWORK / INFRA_SUBNETWORK (self-links) + INFRA_PODS_RANGE / INFRA_SERVICES_RANGE
#            (secondary range names, default pods/services) as repository variables -> the cluster attaches to it; unset = default network.
#            gh api -X PUT repos/$GITHUB_REPOSITORY/environments/gke -F 'reviewers[][type]=User' -F "reviewers[][id]=$(gh api user --jq .id)"
# 2. main stack once, from the laptop (operators + Kafka/Postgres CRs + secrets + services + otel-lgtm + KEDA):
buck2 build //:docker -c llmmcp.registry=$GCP_REGION-docker.pkg.dev/$GCP_PROJECT_ID/llmmcp   # images must exist before main can plan
TF_VAR_app_api_keys=… TF_VAR_app_admin_api_keys=… TF_VAR_postgres_password=… npx -y cdktn-cli@0.24.0 deploy main
#    kubernetes_manifest needs the CRDs at plan time -> on a fresh cluster run it twice (or --target the helm releases first)
# 2b. pull requests (trigger commented out, run by hand on a branch): pull_request.yaml builds + pushes the affected images as <service>:<pr-branch> and comments
#     `buck2 build //infra:plan@{common,main}` (IMAGE_TAG = base branch, so the plan shows the target branch's state)
# 3. services: run push.yaml by hand (Actions -> Push -> Run workflow; the push trigger is commented out until the cloud path is tried) -> buck2 build <affected :verify + :docker> (tests, then Jib -> Artifact Registry <service>:main)
#    -> approval on "gke" -> buck2 run //infra:apply@common + apply@main (the main stack resolves <service>:main to its digest;
#       wait_for_rollout fails the apply on a bad image). First deploy: run workflow_dispatch with build_all so every image exists.
#    -> e2e job: rollout status + readiness, port-forwards (Kafka broker host mapped to localhost in /etc/hosts), make e2e; pod logs on failure
# 4. smoke against GKE by hand: kubectl port-forward as in smoke-k8s; MCP via port-forward 8081
# 5. tear down when not demoing: npx -y cdktn-cli@0.24.0 destroy main && npx -y cdktn-cli@0.24.0 destroy common
```

## Inspect

```bash
docker compose ps                                  # container state + health
docker compose logs -f kafka postgres              # infra logs
docker exec -it $(docker compose ps -q postgres) psql -U llm -d job_db -c '\dt'
curl -s localhost:8081/actuator/prometheus | grep cache_
```

## Reset

```bash
make compose-reset       # down -v: wipes pgdata (all three DBs) and recreates topics
```

Flyway re-runs migrations on next service start; `db/local/V900` reseeds demo data.

## Troubleshooting (Kubernetes)
- Pod `CrashLoopBackOff` right after start: `kubectl logs --previous`; JVM OOM shows as exit 137 / `OOMKilled` in `describe` — raise the memory limit, not `-Xmx` (heap is 75 % of the limit).
- `0/1 Ready` forever: readiness probe fails — `curl` `/actuator/health/readiness` from inside (`kubectl exec … -- wget -qO- localhost:8081/actuator/health/readiness`) and read which of `db`/`redis`/`kafka` is DOWN.
- `ErrImageNeverPull`: the image is not in the node — `make kind-load` again (tags must be `:local`, policy `Never`).
- Kafka CR not Ready: `kubectl -n kafka get kafka llm-mcp -o jsonpath='{.status.conditions}'`; the node pool pod `llm-mcp-dual-role-0` logs.
- CNPG `Database` `applied: false`: the owner role does not exist yet — roles are reconciled from `managed.roles` first; check `kubectl -n db get cluster pg -o jsonpath='{.status.managedRolesStatus}'`.
- Secrets missing after `kind-down`/`kind-up`: rerun `scripts/k8s-secrets.sh` (they live only in the cluster).
- `Validate failed: Migrations have failed validation` after pulling a new migration: an old local DB still records the pre-Phase-7 `V900` seed. Fix: `delete from flyway_schema_history where version='900'` in job_db/credit_db (kind: `kubectl -n db exec pg-1 -c postgres -- psql -U postgres -d job_db -c "..."`) or `make compose-reset` for compose.
- Changing the outbox schema or the EventRouter placement while in CDC mode: apply the migration first, let the connector drain the pre-migration WAL records with the OLD placement (task must be RUNNING and caught up), THEN change `table.fields.additional.placement` — otherwise the task fails with `<field> is not a valid field name` and stays FAILED (`kubectl -n kafka annotate kafkaconnector <name> strimzi.io/restart-task=0` after fixing).
- CDC mode, connectors RUNNING but jobs stuck in CREATED: `kubectl -n kafka logs debezium-connect-0 | grep UNKNOWN_TOPIC` — the `__debezium-heartbeat.*` topics must exist (`deploy/kafka/connect/heartbeat-topics.yaml`); auto-creation is off on purpose.

## Troubleshooting

| Symptom | Check |
|---|---|
| Build fails with "Build with JDK 21" | `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` — PATH default is JDK 26 |
| Service can't reach Postgres | `docker compose ps` healthy? DB is `job_db`, user from `.env` (default `llm`) |
| Tests hang on container start | Docker Desktop running? First run pulls postgres/redis images |
| Port 8081/8090/5433/6379 busy | another instance running: `lsof -i :8081` |
| psql from host | `psql -h localhost -p 5433 -U llm job_db` — compose maps host **5433** → container 5432 (5432 is often taken by a local Postgres) |
| Redis on 6379 is not ours | compose maps host **6380** → container 6379 (a local `redis-server` often owns 6379); Boot's compose support reads the mapped port automatically |
| Kafka data gone after compose-down | expected: kafka has no volume (topics are recreated by kafka-init); only `pgdata` persists. Full wipe: `make compose-reset` |
