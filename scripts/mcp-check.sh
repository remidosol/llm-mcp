#!/usr/bin/env bash
# MCP probe: MCP Inspector CLI against the running job-service (local profile, /mcp open).
# Usage: make run-all && scripts/mcp-check.sh   (needs node/npx; MCP_URL overrides the endpoint)
set -euo pipefail
URL=${MCP_URL:-http://localhost:8081/mcp}
run() { npx -y @modelcontextprotocol/inspector --cli "$URL" --transport http "$@"; }

echo "== tools/list"
run --method tools/list | python3 -c 'import json,sys; d=json.load(sys.stdin); print([t["name"] for t in d["tools"]])'

echo "== tools/call create_job"
CREATED=$(run --method tools/call --tool-name create_job --tool-arg userId=u1 --tool-arg "prompt=Explain the outbox pattern in one sentence." --tool-arg model=fake:demo)
JOB=$(echo "$CREATED" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(json.loads(d["content"][0]["text"])["jobId"])')
echo "jobId=$JOB"

echo "== poll get_job"
for i in $(seq 1 30); do
  STATUS=$(run --method tools/call --tool-name get_job --tool-arg jobId=$JOB | python3 -c 'import json,sys; d=json.load(sys.stdin); print(json.loads(d["content"][0]["text"])["status"])')
  echo "  $STATUS"; [ "$STATUS" = "COMPLETED" ] && break; sleep 1
done

echo "== resources/read job://$JOB/result"
run --method resources/read --uri "job://$JOB/result" | python3 -c 'import json,sys; d=json.load(sys.stdin); r=json.loads(d["contents"][0]["text"]); print(r["output"], "| tokens", r["promptTokens"], r["completionTokens"])'

echo "== prompts/get compare_models + completion"
run --method prompts/get --prompt-name compare_models --prompt-args "prompt=What is a saga?" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["messages"][0]["content"]["text"][:80], "...")'
echo "MCP CHECK OK"
