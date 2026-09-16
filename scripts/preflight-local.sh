#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "$ROOT" ]]; then
  echo "preflight: run this inside the repository" >&2
  exit 2
fi
cd "$ROOT"

echo "[preflight] whitespace / conflict markers"
git diff --check

# Highlight-language fixtures intentionally contain conflict-marker-looking lines
# so the renderer can test diffs/Markdown. Exclude only that fixture tree from
# the unresolved-merge-marker guard; production/source paths remain covered.
if git grep -nE '^(<<<<<<<|=======|>>>>>>>)' -- . ':!*.lock' ':!pnpm-lock.yaml' ':!gradle/libs.versions.toml' ':!highlight/src/test/resources/hljs/**' >/tmp/orchords-preflight-conflicts.txt 2>/dev/null; then
  cat /tmp/orchords-preflight-conflicts.txt
  echo "preflight: unresolved merge markers found" >&2
  exit 1
fi

echo "[preflight] common typo guard"
TYPO_RE='\b(teh|recieve|recieved|seperate|seperated|definately|occured|occurrance|alot)\b'
if git grep -nEi "$TYPO_RE" -- '*.md' '*.kt' '*.kts' '*.java' '*.xml' '*.json' '*.yml' '*.yaml' '*.toml' '*.ts' '*.tsx' '*.js' '*.jsx' '*.sh' ':(exclude)scripts/preflight-local.sh' >/tmp/orchords-preflight-typos.txt 2>/dev/null; then
  cat /tmp/orchords-preflight-typos.txt
  echo "preflight: likely typo(s) found; review before pushing" >&2
  exit 1
fi

echo "[preflight] repository sanity"
git status --short

echo "preflight: PASS"
