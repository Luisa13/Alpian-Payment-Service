#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
IDEMPOTENCY_KEY="$(uuidgen | tr '[:upper:]' '[:lower:]')"

echo "==> POST /v1/transfers"
BODY='{
  "amount": "42.00",
  "currency": "CHF",
  "sourceAccountId": "a0000000-0000-0000-0000-000000000001",
  "targetAccountId": "a0000000-0000-0000-0000-000000000002",
  "description": "smoke test"
}'

RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/v1/transfers" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$BODY")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY_RESP=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" != "201" ]; then
  echo "FAIL: expected 201, got $HTTP_CODE"
  echo "$BODY_RESP"
  exit 1
fi
echo "OK: 201 Created"
echo "$BODY_RESP" | python3 -m json.tool 2>/dev/null || echo "$BODY_RESP"

echo ""
echo "==> Idempotent replay (same key + body)"
REPLAY=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/v1/transfers" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$BODY")

REPLAY_CODE=$(echo "$REPLAY" | tail -1)
REPLAY_BODY=$(echo "$REPLAY" | sed '$d')

if [ "$REPLAY_CODE" != "201" ]; then
  echo "FAIL: idempotent replay expected 201, got $REPLAY_CODE"
  exit 1
fi
if [ "$BODY_RESP" != "$REPLAY_BODY" ]; then
  echo "FAIL: replay response body differs"
  echo "  original: $BODY_RESP"
  echo "  replay:   $REPLAY_BODY"
  exit 1
fi
echo "OK: 201 idempotent replay — body matches"

echo ""
echo "==> Body mismatch → 409"
CONFLICT_BODY='{
  "amount": "99.00",
  "currency": "CHF",
  "sourceAccountId": "a0000000-0000-0000-0000-000000000001",
  "targetAccountId": "a0000000-0000-0000-0000-000000000002",
  "description": "smoke test conflict"
}'
CONFLICT=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/v1/transfers" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$CONFLICT_BODY")

CONFLICT_CODE=$(echo "$CONFLICT" | tail -1)
if [ "$CONFLICT_CODE" != "409" ]; then
  echo "FAIL: expected 409, got $CONFLICT_CODE"
  exit 1
fi
echo "OK: 409 conflict on body mismatch"

echo ""
echo "==> Custom metrics (transfers_created_total)"
METRICS=$(curl -s "$BASE_URL/actuator/prometheus")
if echo "$METRICS" | grep -q "transfers_created_total"; then
  echo "OK: metrics endpoint contains transfers_created_total"
  echo "$METRICS" | grep "^transfers_" | head -10
else
  echo "WARN: transfers_created_total not found in metrics (service may not have processed yet)"
fi

echo ""
echo "All smoke checks passed."
