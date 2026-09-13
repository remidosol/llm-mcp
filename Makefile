# Build with JDK 21 (Temurin). PATH default may be a newer JDK; the enforcer rejects it.
JAVA_HOME := $(shell /usr/libexec/java_home -v 21 2>/dev/null)
export JAVA_HOME
MVN := ./mvnw -B
PROFILE := local

.PHONY: build test verify install-contracts run-job run-credit run-llm run-all stop-all smoke e2e compose-up compose-down compose-reset compose-up-cdc debezium-register

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
# kind-up kind-load deploy-local kind-down   (Phase 6)
# deploy-gke         (Phase 7)
