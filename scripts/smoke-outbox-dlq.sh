#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
COMPOSE="${COMPOSE:-docker compose}"
POISON_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"
POISON_AGG_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"
KAFKA_STOPPED=0

psql_query() {
  $COMPOSE exec -T postgres psql -U payment -d paymentdb -tAc "$1"
}

cleanup() {
  if [ "$KAFKA_STOPPED" -eq 1 ]; then
    echo ""
    echo "==> Cleanup: ensuring Kafka is started"
    $COMPOSE start kafka >/dev/null 2>&1 || true
    KAFKA_STOPPED=0
  fi
  psql_query "DELETE FROM outbox_events WHERE id = '$POISON_ID';" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> Step 1: Stopping Kafka so outbox sends fail"
$COMPOSE stop kafka
KAFKA_STOPPED=1

echo ""
echo "==> Step 2: Injecting poison outbox row (id=$POISON_ID, aggregate_id=$POISON_AGG_ID)"
psql_query "
  INSERT INTO outbox_events (id, aggregate_id, event_type, payload)
  VALUES (
    '$POISON_ID',
    '$POISON_AGG_ID',
    'PAYMENT_COMPLETED',
    '{\"broken\": true}'::jsonb
  );
"
echo "OK: poison row inserted"

echo ""
echo "==> Step 3: Waiting for relay to dead-letter after 5 attempts (may take ~30-60s)"
DEADLINE=$((SECONDS + 90))
ROW_STATE=""
while [ $SECONDS -lt $DEADLINE ]; do
  ROW_STATE=$(psql_query "
    SELECT publish_attempts || '|' || (dead_lettered_at IS NOT NULL) || '|' || (published_at IS NULL) || '|' || (last_error IS NOT NULL)
    FROM outbox_events
    WHERE id = '$POISON_ID';
  " | tr -d '[:space:]')
  if [ "$ROW_STATE" = "5|true|true|true" ]; then
    break
  fi
  sleep 2
done

if [ "$ROW_STATE" != "5|true|true|true" ]; then
  echo "FAIL: expected poison row state '5|true|true|true' (attempts=5, dead_lettered, unpublished, has_error), got '$ROW_STATE'"
  psql_query "
    SELECT id, publish_attempts, dead_lettered_at, published_at, last_error
    FROM outbox_events
    WHERE id = '$POISON_ID';
  "
  exit 1
fi
echo "OK: poison row dead-lettered after 5 attempts"

echo ""
echo "==> Checking outbox.dead_lettered.events gauge (> 0 expected)"
DEAD_LETTERED=$(curl -s "$BASE_URL/actuator/metrics/outbox.dead_lettered.events" | \
  python3 -c "import sys, json; m = json.load(sys.stdin)['measurements']; print(m[0]['value'])")
IS_POSITIVE=$(python3 -c "print('yes' if float('$DEAD_LETTERED') > 0 else 'no')")
if [ "$IS_POSITIVE" != "yes" ]; then
  echo "FAIL: expected outbox.dead_lettered.events > 0, got $DEAD_LETTERED"
  exit 1
fi
echo "OK: outbox.dead_lettered.events = $DEAD_LETTERED"

echo ""
echo "==> Step 4: Restoring Kafka and waiting for broker readiness"
$COMPOSE start kafka
KAFKA_STOPPED=0

KAFKA_DEADLINE=$((SECONDS + 30))
KAFKA_READY=0
while [ $SECONDS -lt $KAFKA_DEADLINE ]; do
  if $COMPOSE exec -T kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:29092 >/dev/null 2>&1; then
    KAFKA_READY=1
    break
  fi
  sleep 1
done

if [ "$KAFKA_READY" -ne 1 ]; then
  echo "FAIL: Kafka did not become ready within 30s"
  exit 1
fi
echo "OK: Kafka is back up"

echo ""
echo "==> Firing a new payment to confirm other events are unblocked"
IDEMPOTENCY_KEY="$(uuidgen | tr '[:upper:]' '[:lower:]')"
BODY='{
  "senderAccountId": "b0000000-0000-0000-0000-000000000001",
  "receiverAccountId": "b0000000-0000-0000-0000-000000000002",
  "amount": "50.00",
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

PAYMENT_ID=$(echo "$BODY_RESP" | python3 -c "import sys, json; print(json.load(sys.stdin)['id'])")
echo "OK: 201 Created (Payment ID: $PAYMENT_ID)"

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
  echo "FAIL: expected new payment events published, got '$PUBLISHED_TYPES'"
  exit 1
fi
echo "OK: new payment outbox events published ($PUBLISHED_TYPES)"

echo ""
echo "==> Verifying new payment in Kafka and poison aggregate_id absent"
KAFKA_MSGS=$($COMPOSE exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:29092 \
  --topic payment-notifications \
  --from-beginning \
  --timeout-ms 4000 \
  --property print.key=true \
  --property print.headers=true 2>/dev/null || true)

if echo "$KAFKA_MSGS" | grep -q "$POISON_AGG_ID"; then
  echo "FAIL: poison aggregate_id $POISON_AGG_ID appeared in Kafka!"
  exit 1
fi

MATCHING_MSGS=$(echo "$KAFKA_MSGS" | grep "$PAYMENT_ID" || true)
if ! echo "$MATCHING_MSGS" | grep -q "PAYMENT_RESERVED" || ! echo "$MATCHING_MSGS" | grep -q "PAYMENT_COMPLETED"; then
  echo "FAIL: expected new payment events in Kafka for $PAYMENT_ID"
  exit 1
fi
echo "OK: new payment events in Kafka; poison row permanently sidelined"

echo ""
echo "Outbox dead-letter smoke check passed."
