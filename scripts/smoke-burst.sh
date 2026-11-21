#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
COMPOSE="${COMPOSE:-docker compose}"
BURST_SIZE="${BURST_SIZE:-10}"

psql_query() {
  $COMPOSE exec -T postgres psql -U payment -d paymentdb -tAc "$1"
}

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

echo "==> Firing $BURST_SIZE concurrent POST /api/v1/payments requests"
PIDS=()
for i in $(seq 1 "$BURST_SIZE"); do
  (
    KEY="$(uuidgen | tr '[:upper:]' '[:lower:]')"
    BODY='{
      "senderAccountId": "a0000000-0000-0000-0000-000000000001",
      "receiverAccountId": "a0000000-0000-0000-0000-000000000002",
      "amount": "1.00",
      "currency": "CHF"
    }'
    RESPONSE=$(curl -s -w "\n%{http_code}" \
      -X POST "$BASE_URL/api/v1/payments" \
      -H "Content-Type: application/json" \
      -H "Idempotency-Key: $KEY" \
      -d "$BODY")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    BODY_RESP=$(echo "$RESPONSE" | sed '$d')
    echo "$HTTP_CODE" > "$TMP_DIR/code_$i"
    echo "$BODY_RESP" > "$TMP_DIR/body_$i"
  ) &
  PIDS+=($!)
done

for pid in "${PIDS[@]}"; do
  wait "$pid"
done

PAYMENT_IDS=()
FAILURES=0
for i in $(seq 1 "$BURST_SIZE"); do
  CODE=$(cat "$TMP_DIR/code_$i")
  if [ "$CODE" != "201" ]; then
    echo "FAIL: request #$i returned HTTP $CODE"
    cat "$TMP_DIR/body_$i"
    echo ""
    FAILURES=$((FAILURES + 1))
  else
    PID_VAL=$(python3 -c "import sys, json; print(json.load(sys.stdin)['paymentId'])" < "$TMP_DIR/body_$i")
    PAYMENT_IDS+=("$PID_VAL")
  fi
done

if [ "$FAILURES" -gt 0 ]; then
  echo "FAIL: $FAILURES of $BURST_SIZE concurrent requests did not return 201"
  exit 1
fi
echo "OK: all $BURST_SIZE concurrent requests returned 201 Created"

SQL_IDS=$(printf "'%s'," "${PAYMENT_IDS[@]}")
SQL_IDS="${SQL_IDS%,}"
EXPECTED_EVENTS=$((BURST_SIZE * 2))

echo ""
echo "==> Waiting for all $EXPECTED_EVENTS outbox events to be published"
DEADLINE=$((SECONDS + 20))
PUBLISHED_COUNT=0
while [ $SECONDS -lt $DEADLINE ]; do
  PUBLISHED_COUNT=$(psql_query "
    SELECT COUNT(*)
    FROM outbox_events
    WHERE aggregate_id IN ($SQL_IDS)
      AND published_at IS NOT NULL;
  " | tr -d '[:space:]')
  if [ "$PUBLISHED_COUNT" = "$EXPECTED_EVENTS" ]; then
    break
  fi
  sleep 0.5
done

if [ "$PUBLISHED_COUNT" != "$EXPECTED_EVENTS" ]; then
  echo "FAIL: expected $EXPECTED_EVENTS published outbox events, got $PUBLISHED_COUNT"
  exit 1
fi
echo "OK: all $PUBLISHED_COUNT outbox events marked published"

echo ""
echo "==> Verifying all $BURST_SIZE payment IDs landed in Kafka topic payment-notifications"
KAFKA_MSGS=$($COMPOSE exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:29092 \
  --topic payment-notifications \
  --from-beginning \
  --timeout-ms 5000 \
  --property print.key=true \
  --property print.headers=true 2>/dev/null || true)

MISSING=0
for pid in "${PAYMENT_IDS[@]}"; do
  MATCHING=$(echo "$KAFKA_MSGS" | grep "$pid" || true)
  if ! echo "$MATCHING" | grep -q "PAYMENT_RESERVED" || ! echo "$MATCHING" | grep -q "PAYMENT_COMPLETED"; then
    echo "FAIL: missing PAYMENT_RESERVED or PAYMENT_COMPLETED in Kafka for $pid"
    MISSING=$((MISSING + 1))
  fi
done

if [ "$MISSING" -gt 0 ]; then
  echo "FAIL: $MISSING payments missing expected Kafka events"
  exit 1
fi
echo "OK: all $BURST_SIZE payments have both events in Kafka"

echo ""
echo "Concurrent burst smoke check ($BURST_SIZE payments) passed."
