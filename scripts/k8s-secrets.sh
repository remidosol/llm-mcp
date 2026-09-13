#!/usr/bin/env bash
# Creates the Kubernetes Secrets the cluster needs from .env (falls back to the local defaults).
# Idempotent (apply of a dry-run manifest); nothing here is ever written to the repo.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; [ -f .env ] && . ./.env; set +a
PG_PASSWORD=${POSTGRES_PASSWORD:-llm-local}
APP_API_KEYS=${APP_API_KEYS:-local-dev-key}
APP_ADMIN_API_KEYS=${APP_ADMIN_API_KEYS:-local-admin-key}

for ns in db llm-mcp; do kubectl get ns "$ns" >/dev/null 2>&1 || kubectl create ns "$ns"; done

# one basic-auth secret per database role, in `db` (CNPG sets the password) and in `llm-mcp` (the service reads it)
for role in job credit llm; do
  for ns in db llm-mcp; do
    kubectl create secret generic "pg-$role" -n "$ns" --type=kubernetes.io/basic-auth \
      --from-literal=username="$role" --from-literal=password="$PG_PASSWORD" \
      --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  done
done
# the same secrets under the `kafka` namespace feed the Debezium connectors (make deploy-cdc)
if kubectl get ns kafka >/dev/null 2>&1; then
  for role in job credit llm; do
    kubectl create secret generic "pg-$role" -n kafka --type=kubernetes.io/basic-auth \
      --from-literal=username="$role" --from-literal=password="$PG_PASSWORD" \
      --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  done
fi

# API keys + optional provider keys. Empty provider keys are OMITTED on purpose: Spring's
# @ConditionalOnProperty treats an empty value as "present" and would try to build a real client.
args=(--from-literal=APP_API_KEYS="$APP_API_KEYS" --from-literal=APP_ADMIN_API_KEYS="$APP_ADMIN_API_KEYS")
[ -n "${OPENAI_API_KEY:-}" ] && args+=(--from-literal=SPRING_AI_OPENAI_API_KEY="$OPENAI_API_KEY")
[ -n "${GOOGLE_API_KEY:-}" ] && args+=(--from-literal=SPRING_AI_GOOGLE_GENAI_API_KEY="$GOOGLE_API_KEY")
kubectl create secret generic app-secrets -n llm-mcp "${args[@]}" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
echo "secrets applied: pg-{job,credit,llm} (db, llm-mcp$(kubectl get ns kafka >/dev/null 2>&1 && echo ', kafka')), app-secrets (llm-mcp)"
