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

Local-profile knobs that make demos fast (PRD defaults in parentheses): job timeout 20 s (120 s),
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
