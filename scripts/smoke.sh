#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
IDEMPOTENCY_KEY="$(uuidgen | tr '[:upper:]' '[:lower:]')"

echo "==> POST /api/v1/payments"
BODY='{
  "amount": "42.00",
  "currency": "CHF",
  "senderAccountId": "a0000000-0000-0000-0000-000000000001",
  "receiverAccountId": "a0000000-0000-0000-0000-000000000002"
}'

RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/payments" \
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
  -X POST "$BASE_URL/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$BODY")

REPLAY_CODE=$(echo "$REPLAY" | tail -1)
REPLAY_BODY=$(echo "$REPLAY" | sed '$d')

if [ "$REPLAY_CODE" != "200" ]; then
  echo "FAIL: idempotent replay expected 200, got $REPLAY_CODE"
  exit 1
fi
if [ "$BODY_RESP" != "$REPLAY_BODY" ]; then
  echo "FAIL: replay response body differs"
  echo "  original: $BODY_RESP"
  echo "  replay:   $REPLAY_BODY"
  exit 1
fi
echo "OK: 200 idempotent replay — body matches"

echo ""
echo "==> Body mismatch → 422"
CONFLICT_BODY='{
  "amount": "99.00",
  "currency": "CHF",
  "senderAccountId": "a0000000-0000-0000-0000-000000000001",
  "receiverAccountId": "a0000000-0000-0000-0000-000000000002"
}'
CONFLICT=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$CONFLICT_BODY")

CONFLICT_CODE=$(echo "$CONFLICT" | tail -1)
if [ "$CONFLICT_CODE" != "422" ]; then
  echo "FAIL: expected 422, got $CONFLICT_CODE"
  exit 1
fi
echo "OK: 422 conflict on body mismatch"

echo ""
echo "==> Custom metrics (payments_notifications_sent_total, outbox_backlog_events)"
METRICS=$(curl -s "$BASE_URL/actuator/prometheus")
if echo "$METRICS" | grep -q "payments_notifications_sent_total" && echo "$METRICS" | grep -q "outbox_backlog_events"; then
  echo "OK: metrics endpoint contains custom payment and outbox metrics"
  echo "$METRICS" | grep -E "^(payments_|outbox_)" | head -10
else
  echo "FAIL: expected custom metrics not found in /actuator/prometheus"
  exit 1
fi

echo ""
echo "All smoke checks passed."
