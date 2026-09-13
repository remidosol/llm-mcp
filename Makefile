# Build with JDK 21 (Temurin). PATH default may be a newer JDK; the enforcer rejects it.
JAVA_HOME := $(shell /usr/libexec/java_home -v 21 2>/dev/null)
export JAVA_HOME
MVN := ./mvnw -B
PROFILE := local

.PHONY: build test verify install-contracts run-job run-credit run-llm run-all stop-all smoke e2e compose-up compose-down compose-reset compose-up-cdc debezium-register images kind-up kind-down kind-load deploy-local deploy-cdc smoke-k8s kafka-ui pg-forward deploy-observability grafana compose-up-observability infra-synth buck-targets buck-verify buck-images changed k8s-status k8s-reset

build: ## compile + package, skip tests
	$(MVN) -DskipTests package

test: ## full reactor: unit + integration + ArchUnit (Testcontainers, no H2)
	$(MVN) verify

verify: test

# spring-boot:run with -pl leaves contracts outside the reactor, so contracts AND the root pom
# (its parent) must be in ~/.m2 first (-N = install the root pom only, non-recursive)
install-contracts:
	$(MVN) -q -N install
	$(MVN) -q -pl contracts -DskipTests install

# run-* source .env (same file compose reads) so the app and the containers agree on credentials
run-job: install-contracts
	set -a; [ -f .env ] && . ./.env; set +a; $(MVN) -pl services/job-service spring-boot:run -Dspring-boot.run.profiles=$(PROFILE)

run-credit: install-contracts
	set -a; [ -f .env ] && . ./.env; set +a; $(MVN) -pl services/credit-service spring-boot:run -Dspring-boot.run.profiles=$(PROFILE)

run-llm: install-contracts
	set -a; [ -f .env ] && . ./.env; set +a; $(MVN) -pl services/llm-worker spring-boot:run -Dspring-boot.run.profiles=$(PROFILE)

compose-up:
	docker compose up -d --wait

compose-down:
	docker compose down

compose-reset: ## drop volumes: pgdata is wiped, topics are recreated
	docker compose down -v && docker compose up -d --wait

compose-up-cdc: ## Phase 3: infra + Debezium Connect (profile cdc)
	docker compose --profile cdc up -d --wait

debezium-register: ## Phase 3: create/update the outbox connectors (idempotent PUT)
	@for f in deploy/debezium/*-connector.json; do \
	  name=$$(basename $$f .json); \
	  echo "== $$name"; \
	  curl -s -X PUT -H 'Content-Type: application/json' localhost:8093/connectors/$$name/config -d @$$f | head -c 300; echo; \
	done
	@curl -s localhost:8093/connectors?expand=status | head -c 600; echo

RUN_DIR := .run

run-all: install-contracts ## Phase 4: all three services in the background (logs in .run/)
	@mkdir -p $(RUN_DIR)
	@for s in job-service credit-service llm-worker; do \
	  (set -a; [ -f .env ] && . ./.env; set +a; nohup $(MVN) -q -pl services/$$s spring-boot:run -Dspring-boot.run.profiles=$(PROFILE) > $(RUN_DIR)/$$s.log 2>&1 & echo $$! > $(RUN_DIR)/$$s.pid); \
	  echo "started $$s (pid $$(cat $(RUN_DIR)/$$s.pid), log $(RUN_DIR)/$$s.log)"; \
	done
	@for p in 8081 8082 8083; do until curl -sf localhost:$$p/actuator/health/readiness >/dev/null 2>&1; do sleep 2; done; echo "ready :$$p"; done

stop-all: ## stop the services started by run-all
	@for p in 8081 8082 8083; do pid=$$(lsof -ti :$$p); [ -n "$$pid" ] && kill $$pid && echo "stopped :$$p" || true; done

mcp-tools: ## list MCP tools of the running job-service (MCP Inspector CLI)
	npx -y @modelcontextprotocol/inspector --cli http://localhost:8081/mcp --transport http --method tools/list

mcp-check: ## Phase 5 DoD: create -> poll -> read result -> prompt, all through the MCP Inspector CLI
	scripts/mcp-check.sh

mcp-add: ## register job-service as an MCP server in Claude Code (user scope; .mcp.json covers project scope)
	claude mcp add --transport http job-service http://localhost:8081/mcp

smoke: ## Phase 4: happy path + [FAIL] path with the Kafka event trail (needs run-all)
	./scripts/smoke.sh

e2e: ## Phase 4: black-box saga tests against the running services
	$(MVN) -Pe2e -pl e2e -am verify
# ---------------------------------------------------------------- Phase 6: Kubernetes on kind
KIND_CLUSTER := llm-mcp
STRIMZI_VERSION := 1.2.0
CNPG_MANIFEST := https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/release-1.30/releases/cnpg-1.30.0.yaml
IMAGES := llm-mcp/job-service:local llm-mcp/credit-service:local llm-mcp/llm-worker:local

images: install-contracts ## build the three service images into the local Docker daemon with Jib (no Dockerfile)
	$(MVN) -q -DskipTests -pl services/job-service,services/credit-service,services/llm-worker package jib:dockerBuild
	docker build -q -t llm-mcp/kafka-connect-debezium:local deploy/kafka/connect

kind-up: ## create the kind cluster (1 control-plane + 1 worker, NodePort 30080 mapped)
	kind create cluster --config deploy/kind/cluster.yaml --wait 120s

kind-down:
	kind delete cluster --name $(KIND_CLUSTER)

kind-load: images ## Jib images + Debezium Connect image into the kind nodes
	kind load docker-image --name $(KIND_CLUSTER) $(IMAGES) llm-mcp/kafka-connect-debezium:local

deploy-local: ## operators (Strimzi via Helm, CNPG via manifest) + Kafka + Postgres + secrets + app overlay
	helm upgrade --install strimzi oci://quay.io/strimzi-helm/strimzi-kafka-operator --version $(STRIMZI_VERSION) \
	  -n kafka --create-namespace --wait --timeout 10m
	kubectl apply --server-side -f $(CNPG_MANIFEST)
	kubectl -n cnpg-system rollout status deployment/cnpg-controller-manager --timeout=5m
	kubectl apply -f deploy/kafka/kafka.yaml -f deploy/kafka/topics.yaml -f deploy/kafka/kafka-ui.yaml
	./scripts/k8s-secrets.sh
	@until kubectl apply -f deploy/postgres/cluster.yaml >/dev/null 2>&1; do echo "waiting for the CNPG webhook..."; sleep 3; done
	kubectl -n kafka wait kafka/$(KIND_CLUSTER) --for=condition=Ready --timeout=10m
	kubectl -n db wait cluster/pg --for=condition=Ready --timeout=10m
	@for d in pg-job-db pg-credit-db pg-llm-db; do kubectl -n db wait database/$$d --for=jsonpath='{.status.applied}'=true --timeout=5m; done
	kubectl apply -k deploy/k8s/overlays/local
	@for d in job-service credit-service llm-worker; do kubectl -n llm-mcp rollout status deployment/$$d --timeout=5m; done
	@echo "job-service: http://localhost:30080 (NodePort) — make smoke-k8s"

deploy-cdc: ## Phase 6 stretch: Debezium under Strimzi; services switch to the CDC publisher
	kubectl apply -f deploy/kafka/connect/heartbeat-topics.yaml -f deploy/kafka/connect/kafka-connect.yaml
	kubectl -n kafka wait kafkaconnect/debezium --for=condition=Ready --timeout=10m
	kubectl apply -f deploy/kafka/connect/connectors.yaml
	kubectl -n llm-mcp set env deployment/job-service deployment/credit-service deployment/llm-worker APP_OUTBOX_PUBLISHER=cdc
	@for d in job-service credit-service llm-worker; do kubectl -n llm-mcp rollout status deployment/$$d --timeout=5m; done
	kubectl -n kafka get kafkaconnector

smoke-k8s: ## the smoke script through port-forwards, event trail read from the Strimzi broker pod
	@mkdir -p .run
	@kubectl -n llm-mcp port-forward svc/job-service 18081:8081 >/dev/null 2>&1 & echo $$! > .run/pf-job.pid
	@kubectl -n llm-mcp port-forward svc/credit-service 18082:8082 >/dev/null 2>&1 & echo $$! > .run/pf-credit.pid
	@sleep 2; JOB_URL=http://localhost:18081 CREDIT_URL=http://localhost:18082 \
	  KAFKA_EXEC="kubectl -n kafka exec $(KIND_CLUSTER)-dual-role-0 --" KAFKA_BOOTSTRAP=$(KIND_CLUSTER)-kafka-bootstrap:9092 TRAIL_TIMEOUT_MS=10000 ./scripts/smoke.sh; \
	  rc=$$?; kill $$(cat .run/pf-job.pid .run/pf-credit.pid) 2>/dev/null; rm -f .run/pf-*.pid; exit $$rc

kafka-ui: ## Kafka UI (topics, messages, consumer lag) at http://localhost:8090 — Ctrl-C to stop
	kubectl -n kafka port-forward svc/kafka-ui 8090:8080

pg-forward: ## Postgres on localhost:5433 for pgAdmin/psql (roles job/credit/llm, password from .env POSTGRES_PASSWORD)
	@# kubectl port-forward exits as soon as ONE forwarded connection is reset by the server (e.g. a failed login);
	@# the loop restarts it so a GUI client with several connections keeps working. Ctrl-C to stop.
	@while true; do kubectl -n db port-forward svc/pg-rw 5433:5432; sleep 1; done

deploy-observability: ## otel-lgtm (collector + Prometheus + Tempo + Loki + Grafana) in ns observability
	@# the node pulls the image itself: `kind load` chokes on this multi-arch image ("content digest not found")
	kubectl apply -f deploy/observability/otel-lgtm.yaml
	kubectl -n observability rollout status deployment/otel-lgtm --timeout=10m

grafana: ## Grafana at http://localhost:3000 (dashboards: llm-mcp saga; Explore: Tempo traces, Loki logs)
	kubectl -n observability port-forward svc/otel-lgtm 3000:3000

compose-up-observability: ## compose infra + otel-lgtm (profile observability); run services with OTEL_ENABLED=true
	docker compose --profile observability up -d --wait

buck-targets: ## Buck2: the target graph (BUCK files = what CI selects from)
	buck2 targets //...

buck-verify: ## Buck2: every :verify target (sandboxed Maven builds, Testcontainers)
	buck2 build //:verify

buck-images: ## Buck2: the three service images into the local daemon (Jib)
	buck2 build //services/job-service:image //services/credit-service:image //services/llm-worker:image

changed: ## Buck2 target determination for the uncommitted tree: make changed BASE=<sha>
	scripts/changed-targets.sh $(or $(BASE),HEAD)

infra-synth: ## CDKTN: render the common + main stacks to Terraform JSON (no credentials, no deploy)
	cd infra && npx -y cdktn-cli@0.24.0 synth && ls cdktf.out/stacks

k8s-status: ## pods across the namespaces + Kafka/Postgres readiness
	kubectl get pods -n kafka; kubectl get pods -n db; kubectl get pods -n llm-mcp -o wide
	kubectl get kafka,kafkatopic -n kafka; kubectl get cluster,database -n db

k8s-reset: ## drop the app tier only (operators, Kafka and Postgres stay)
	kubectl delete -k deploy/k8s/overlays/local --ignore-not-found
# deploy-gke         (Phase 7)
