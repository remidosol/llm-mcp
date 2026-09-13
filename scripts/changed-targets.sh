#!/usr/bin/env bash
# Target determination (the logic of Meta's Buck2 Change Detector, on plain `buck2 uquery`):
#   changed files  ->  owner(file) = the targets whose sources contain it
#                  ->  rdeps(//..., owners) = everything that transitively depends on them
# Files no target owns (docs, README) change nothing; a change to the build graph itself
# (BUCK, .bzl, .buckconfig, mise.toml) means "everything". Output is JSON for the workflow matrix:
#   {"verify":[...], "docker":[...], "infra":true|false, "all":true|false}
set -euo pipefail
BASE=${1:?base sha}; HEAD=${2:-HEAD}
cd "$(dirname "$0")/.."

# bash 3 (macOS) compatible: newline-separated strings instead of mapfile arrays
changed=$(git diff --name-only --diff-filter=ACMR "$BASE" "$HEAD" | grep -vE '^(references|docs)/' || true)
deleted=$(git diff --name-only --diff-filter=D "$BASE" "$HEAD" | grep -vE '^(references|docs)/' || true)

if printf '%s\n%s\n' "$changed" "$deleted" | grep -qE '(^|/)BUCK$|^tools/buck2/|^\.buckconfig$|^mise\.toml$'; then
  echo '{"all":true,"verify":["//:verify"],"docker":["//:docker"],"infra":true}'
  exit 0
fi

expr=""
while IFS= read -r f; do [ -n "$f" ] && expr="${expr:+$expr + }owner('$f')"; done <<< "$changed"
# a deleted file has no owner any more: take every target of the nearest package instead
while IFS= read -r f; do
  [ -z "$f" ] && continue
  d=$(dirname "$f")
  while [ "$d" != "." ] && [ ! -f "$d/BUCK" ]; do d=$(dirname "$d"); done
  if [ "$d" = "." ]; then pkg="//:"; else pkg="//$d:"; fi
  expr="${expr:+$expr + }$pkg"
done <<< "$deleted"
[ -z "$expr" ] && { echo '{"all":false,"verify":[],"docker":[],"infra":false}'; exit 0; }

targets=$(buck2 uquery --console simple "rdeps(//..., $expr)" 2>/dev/null | sed -E 's#^[a-z]+//#//#' | sort -u)
printf '%s\n' "$targets" | python3 -c '
import json, re, sys
targets = [t.strip() for t in sys.stdin if t.strip()]
print(json.dumps({
    "all": False,
    "verify": sorted(t for t in targets if re.match(r"^//(contracts|services/[^:]+):verify$", t)),
    "docker": sorted(t for t in targets if re.match(r"^//services/[^:]+:docker$", t)),
    "infra": any(t.startswith("//infra:") for t in targets),
}))'

