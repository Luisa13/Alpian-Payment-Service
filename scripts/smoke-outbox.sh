#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
COMPOSE="${COMPOSE:-docker compose}"
IDEMPOTENCY_KEY="$(uuidgen | tr '[:upper:]' '[:lower:]')"

psql_query() {
  $COMPOSE exec -T postgres psql -U payment -d paymentdb -tAc "$1"
}

echo "==> POST /api/v1/payments (Outbox Relay happy path)"
BODY='{
  "senderAccountId": "a0000000-0000-0000-0000-000000000001",
  "receiverAccountId": "a0000000-0000-0000-0000-000000000002",
  "amount": "100.00",
  "currency": "CHF"
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

PAYMENT_ID=$(echo "$BODY_RESP" | python3 -c "import sys, json; print(json.load(sys.stdin)['id'])")
if [ -z "$PAYMENT_ID" ]; then
  echo "FAIL: could not extract paymentId from response"
  exit 1
fi
echo "Payment ID: $PAYMENT_ID"

echo ""
echo "==> Waiting for outbox events to be published (PAYMENT_RESERVED, PAYMENT_COMPLETED)"
DEADLINE=$((SECONDS + 15))
PUBLISHED_TYPES=""
while [ $SECONDS -lt $DEADLINE ]; do
  PUBLISHED_TYPES=$(psql_query "
    SELECT string_agg(event_type, ',' ORDER BY created_at)
    FROM outbox_events
    WHERE aggregate_id = '$PAYMENT_ID'
      AND published_at IS NOT NULL;
  " | tr -d '[:space:]')
  if [ "$PUBLISHED_TYPES" = "PAYMENT_RESERVED,PAYMENT_COMPLETED" ]; then
    break
  fi
  sleep 0.5
done

if [ "$PUBLISHED_TYPES" != "PAYMENT_RESERVED,PAYMENT_COMPLETED" ]; then
  echo "FAIL: expected PAYMENT_RESERVED,PAYMENT_COMPLETED published, got '$PUBLISHED_TYPES'"
  psql_query "
    SELECT id, event_type, publish_attempts, claimed_at, published_at, last_error
    FROM outbox_events
    WHERE aggregate_id = '$PAYMENT_ID'
    ORDER BY created_at;
  "
  exit 1
fi
echo "OK: outbox rows marked published ($PUBLISHED_TYPES)"

echo ""
echo "==> Verifying events landed in Kafka topic payment-notifications"
KAFKA_MSGS=$($COMPOSE exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:29092 \
  --topic payment-notifications \
  --from-beginning \
  --timeout-ms 4000 \
  --property print.key=true \
  --property print.headers=true 2>/dev/null || true)

MATCHING_MSGS=$(echo "$KAFKA_MSGS" | grep "$PAYMENT_ID" || true)
if ! echo "$MATCHING_MSGS" | grep -q "PAYMENT_RESERVED" || ! echo "$MATCHING_MSGS" | grep -q "PAYMENT_COMPLETED"; then
  echo "FAIL: expected both PAYMENT_RESERVED and PAYMENT_COMPLETED in Kafka for $PAYMENT_ID"
  echo "$MATCHING_MSGS"
  exit 1
fi
echo "OK: both PAYMENT_RESERVED and PAYMENT_COMPLETED found in Kafka"

echo ""
echo "Outbox happy-path smoke check passed."
