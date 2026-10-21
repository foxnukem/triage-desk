#!/usr/bin/env bash
# Files the ★demo cases from eval/tickets.jsonl as operator tickets, in demo order, and waits
# until the worker has triaged them all. The full pipeline takes minutes per ticket on a laptop,
# so run this well before a demo. Writes eval/reports/demo-tickets.md: demo # → ticket id.
#
#   API=http://localhost:8081 scripts/seed-demo.sh
set -euo pipefail

API=${API:-http://localhost:${BACKEND_PORT:-8080}}
DATASET=${DATASET:-$(dirname "$0")/../eval/tickets.jsonl}
OUT=${OUT:-$(dirname "$0")/../eval/reports/demo-tickets.md}

curl -sf "$API/api/triage/options" >/dev/null || { echo "backend not reachable at $API" >&2; exit 1; }

cases=$(jq -c 'select(.demo != null)' "$DATASET" | jq -sc 'sort_by(.demo)[]')
ids=()
rows=()
while IFS= read -r c; do
  demo=$(jq -r .demo <<<"$c")
  case_id=$(jq -r .id <<<"$c")
  id=$(jq -c '{text}' <<<"$c" | curl -sf -X POST "$API/api/tickets" -H 'Content-Type: application/json' -d @- | jq -r .id)
  echo "★$demo $case_id → ticket #$id"
  ids+=("$id")
  rows+=("$demo|$case_id|$id|$(jq -r '.tags | join(", ")' <<<"$c")|$(jq -r '"\(.expected.category) \(.expected.priority)"' <<<"$c")")
done <<<"$cases"

echo "waiting for triage (one ticket at a time)…"
while :; do
  pending=0
  for id in "${ids[@]}"; do
    s=$(curl -sf "$API/api/tickets/$id" | jq -r 'if .triageError then "ERROR" else .status end')
    [ "$s" = NEW ] && pending=$((pending + 1))
  done
  [ "$pending" -eq 0 ] && break
  echo "  $(date +%H:%M:%S) $pending pending"
  sleep 30
done

{
  echo "# Demo tickets"
  echo
  echo "| ★ | Case | Ticket | Tags | Expected | Actual | Human | Reason | Status |"
  echo "|---|---|---|---|---|---|---|---|---|"
  for r in "${rows[@]}"; do
    IFS='|' read -r demo case_id id tags expected <<<"$r"
    actual=$(curl -sf "$API/api/tickets/$id" | jq -r '
      if .triageError then "– | – | – | ERROR: \(.triageError)"
      else (.result | "\(.classification.category) \(.classification.priority)\(if .ruleOverride then " ⚡" else "" end) | \(.needsHuman) | \(.escalationReason // "–")") + " | \(.status)"
      end')
    echo "| $demo | $case_id | #$id | $tags | $expected | $actual |"
  done
} >"$OUT"
cat "$OUT"
