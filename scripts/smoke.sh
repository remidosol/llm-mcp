#!/usr/bin/env bash
# Smoke test (`make smoke`): one happy job and one [FAIL] job for u1, then the event trail.
# Requires: make compose-up && make run-all   (or make deploy-local && make smoke-k8s)
set -euo pipefail
JOB=${JOB_URL:-http://localhost:8081}; CREDIT=${CREDIT_URL:-http://localhost:8082}; KEY=${API_KEY:-local-dev-key}
# where to run kafka-console-consumer: the compose container (default) or the Strimzi pod (make smoke-k8s)
KAFKA_EXEC=${KAFKA_EXEC:-docker exec llm-mcp-kafka-1}; KAFKA_BOOTSTRAP=${KAFKA_BOOTSTRAP:-localhost:19092}; TRAIL_TIMEOUT_MS=${TRAIL_TIMEOUT_MS:-3000}
create() { curl -sf -X POST "$JOB/api/jobs" -H "X-API-Key: $KEY" -H 'X-User-Id: u1' -H 'Content-Type: application/json' -d "{\"prompt\":\"$1\",\"model\":\"fake:demo\"}" | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4; }
wait_terminal() { for _ in $(seq 1 90); do s=$(curl -sf -H "X-API-Key: $KEY" "$JOB/api/jobs/$1" | grep -o '"status":"[^"]*"' | cut -d'"' -f4); case "$s" in COMPLETED|FAILED|REJECTED|TIMED_OUT) echo "$s"; return;; esac; sleep 1; done; echo "TIMEOUT"; }
trail() { for t in job.events.v1 credit.events.v1 llm.events.v1; do $KAFKA_EXEC /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server $KAFKA_BOOTSTRAP --topic $t --from-beginning --formatter-property print.headers=true --timeout-ms $TRAIL_TIMEOUT_MS 2>/dev/null | { grep "$1" || true; } | grep -oE 'eventType:[A-Za-z]+' | sed "s/eventType:/  $t  /"; done; }

echo "== credits before: $(curl -sf -H "X-API-Key: $KEY" $CREDIT/api/credits/u1)"
HAPPY=$(create "Explain the outbox pattern in two sentences.")
echo "== happy job $HAPPY -> $(wait_terminal $HAPPY)"
echo "   result: $(curl -s -H "X-API-Key: $KEY" $JOB/api/jobs/$HAPPY/result | head -c 160)"
FAILJOB=$(create "[FAIL] compensation demo")
echo "== [FAIL] job $FAILJOB -> $(wait_terminal $FAILJOB)"
sleep 2
echo "== credits after: $(curl -sf -H "X-API-Key: $KEY" $CREDIT/api/credits/u1)"
echo "== event trail (happy):"; trail $HAPPY
echo "== event trail ([FAIL]):"; trail $FAILJOB
S1=$(curl -sf -H "X-API-Key: $KEY" "$JOB/api/jobs/$HAPPY" | grep -o '"status":"[^"]*"' | cut -d'"' -f4); S2=$(curl -sf -H "X-API-Key: $KEY" "$JOB/api/jobs/$FAILJOB" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
[ "$S1" = COMPLETED ] && [ "$S2" = FAILED ] && echo "SMOKE OK" || { echo "SMOKE FAILED ($S1 / $S2)"; exit 1; }
