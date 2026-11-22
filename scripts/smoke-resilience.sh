#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
COMPOSE="${COMPOSE:-docker compose}"
ALICE_ID="a0000000-0000-0000-0000-000000000001"
BOB_ID="a0000000-0000-0000-0000-000000000002"
KAFKA_STOPPED=0
TMP_DIR=$(mktemp -d)

psql_query() {
  $COMPOSE exec -T postgres psql -U payment -d paymentdb -tAc "$1"
}

new_uuid() {
  uuidgen | tr '[:upper:]' '[:lower:]'
}

cleanup() {
  if [ "$KAFKA_STOPPED" -eq 1 ]; then
    echo ""
    echo "==> Cleanup: ensuring Kafka is started"
    $COMPOSE start kafka >/dev/null 2>&1 || true
    KAFKA_STOPPED=0
  fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

wait_for_notifications() {
  local payment_id="$1"
  local expected_csv="$2"
  local deadline=$((SECONDS + 20))
  local actual=""
  while [ $SECONDS -lt $deadline ]; do
    actual=$(psql_query "
      SELECT COALESCE(string_agg(event_type, ',' ORDER BY event_type), '')
      FROM sent_notifications
      WHERE payment_id = '$payment_id';
    " | tr -d '[:space:]')
    if [ "$actual" = "$expected_csv" ]; then
      echo "$actual"
      return 0
    fi
    sleep 0.5
  done
  echo "$actual"
  return 1
}

echo "==> 1. High Availability: liveness and readiness probes"
LIVENESS=$(curl -s -w "\n%{http_code}" "$BASE_URL/actuator/health/liveness")
LIVENESS_CODE=$(echo "$LIVENESS" | tail -1)
READINESS=$(curl -s -w "\n%{http_code}" "$BASE_URL/actuator/health/readiness")
READINESS_CODE=$(echo "$READINESS" | tail -1)
if [ "$LIVENESS_CODE" != "200" ] || [ "$READINESS_CODE" != "200" ]; then
  echo "FAIL: probes expected 200/200, got liveness=$LIVENESS_CODE readiness=$READINESS_CODE"
  exit 1
fi
echo "OK: liveness=200, readiness=200"

echo ""
echo "==> 2. Error Handling: validation (400), not found (404), insufficient funds (422)"
BAD_AMOUNT_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(new_uuid)" \
  -d "{\"amount\":\"-5.00\",\"currency\":\"CHF\",\"senderAccountId\":\"$ALICE_ID\",\"receiverAccountId\":\"$BOB_ID\"}")
if [ "$BAD_AMOUNT_CODE" != "400" ]; then
  echo "FAIL: negative amount expected 400, got $BAD_AMOUNT_CODE"
  exit 1
fi

MISSING_KEY_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/api/v1/payments" \
  -H "Content-Type: application/json" \
  -d "{\"amount\":\"1.00\",\"currency\":\"CHF\",\"senderAccountId\":\"$ALICE_ID\",\"receiverAccountId\":\"$BOB_ID\"}")
if [ "$MISSING_KEY_CODE" != "400" ]; then
  echo "FAIL: missing Idempotency-Key expected 400, got $MISSING_KEY_CODE"
  exit 1
fi

SELF_PAY_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(new_uuid)" \
  -d "{\"amount\":\"1.00\",\"currency\":\"CHF\",\"senderAccountId\":\"$ALICE_ID\",\"receiverAccountId\":\"$ALICE_ID\"}")
if [ "$SELF_PAY_CODE" != "400" ]; then
  echo "FAIL: self-payment expected 400, got $SELF_PAY_CODE"
  exit 1
fi

UNKNOWN_ID=$(new_uuid)
NOT_FOUND_PAY=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/payments/$UNKNOWN_ID")
NOT_FOUND_ACC=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/accounts/$UNKNOWN_ID")
if [ "$NOT_FOUND_PAY" != "404" ] || [ "$NOT_FOUND_ACC" != "404" ]; then
  echo "FAIL: unknown payment/account expected 404/404, got $NOT_FOUND_PAY/$NOT_FOUND_ACC"
  exit 1
fi
echo "OK: 400 validation errors and 404 not-found responses verified"

INSUFF_KEY=$(new_uuid)
INSUFF_RESP=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $INSUFF_KEY" \
  -d "{\"amount\":\"99999999.00\",\"currency\":\"CHF\",\"senderAccountId\":\"$ALICE_ID\",\"receiverAccountId\":\"$BOB_ID\"}")
INSUFF_CODE=$(echo "$INSUFF_RESP" | tail -1)
if [ "$INSUFF_CODE" != "422" ]; then
  echo "FAIL: insufficient funds expected 422, got $INSUFF_CODE"
  exit 1
fi

REJECTED_ID=$(psql_query "SELECT id FROM payments WHERE idempotency_key = '$INSUFF_KEY' AND status = 'REJECTED';" | tr -d '[:space:]')
if [ -z "$REJECTED_ID" ]; then
  echo "FAIL: REJECTED payment row not persisted for idempotency_key=$INSUFF_KEY"
  exit 1
fi
if ! NOTIFS=$(wait_for_notifications "$REJECTED_ID" "PAYMENT_REJECTED"); then
  echo "FAIL: expected PAYMENT_REJECTED notification for $REJECTED_ID, got '$NOTIFS'"
  exit 1
fi
echo "OK: 422 insufficient funds persisted REJECTED payment ($REJECTED_ID) and sent PAYMENT_REJECTED notification"

echo ""
echo "==> 3. External IBAN rail payment & GET /api/v1/payments/{id}"
EXT_RESP=$(curl -s -D "$TMP_DIR/ext_headers" -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(new_uuid)" \
  -d "{\"amount\":\"10.00\",\"currency\":\"CHF\",\"senderAccountId\":\"$ALICE_ID\",\"receiverIban\":\"CH9300762011623852957\"}")
EXT_CODE=$(echo "$EXT_RESP" | tail -1)
EXT_BODY=$(echo "$EXT_RESP" | sed '$d')
if [ "$EXT_CODE" != "201" ] && [ "$EXT_CODE" != "202" ]; then
  echo "FAIL: external IBAN payment expected 201 or 202, got $EXT_CODE"
  echo "$EXT_BODY"
  exit 1
fi
EXT_ID=$(echo "$EXT_BODY" | python3 -c "import sys, json; print(json.load(sys.stdin)['id'])")
if [ "$EXT_CODE" = "202" ] && ! grep -qi "^Location: .*/api/v1/payments/$EXT_ID" "$TMP_DIR/ext_headers"; then
  echo "FAIL: 202 response missing expected Location header"
  exit 1
fi
GET_EXT_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/payments/$EXT_ID")
if [ "$GET_EXT_CODE" != "200" ]; then
  echo "FAIL: GET /api/v1/payments/$EXT_ID expected 200, got $GET_EXT_CODE"
  exit 1
fi
echo "OK: external IBAN payment $EXT_ID returned HTTP $EXT_CODE and is retrievable via GET"

echo ""
echo "==> 4. Transactional Processing: concurrent overdraft race & ledger invariants"
TEST_SENDER=$(new_uuid)
TEST_RECEIVER=$(new_uuid)
psql_query "
  INSERT INTO accounts (id, owner_name, account_type, currency, balance, available_balance) VALUES
    ('$TEST_SENDER', 'Smoke Race Sender', 'CUSTOMER', 'CHF', 100.0000, 100.0000),
    ('$TEST_RECEIVER', 'Smoke Race Receiver', 'CUSTOMER', 'CHF', 0.0000, 0.0000);
  INSERT INTO ledger_entries (id, payment_id, account_id, amount)
  VALUES (gen_random_uuid(), NULL, '$TEST_SENDER', 100.0000);
" >/dev/null

PIDS=()
for i in 1 2 3 4 5; do
  (
    RESP=$(curl -s -w "\n%{http_code}" \
      -X POST "$BASE_URL/api/v1/payments" \
      -H "Content-Type: application/json" \
      -H "Idempotency-Key: $(new_uuid)" \
      -d "{\"amount\":\"30.00\",\"currency\":\"CHF\",\"senderAccountId\":\"$TEST_SENDER\",\"receiverAccountId\":\"$TEST_RECEIVER\"}")
    echo "$RESP" | tail -1 > "$TMP_DIR/race_code_$i"
  ) &
  PIDS+=($!)
done
for pid in "${PIDS[@]}"; do
  wait "$pid"
done

OK_COUNT=0
REJ_COUNT=0
for i in 1 2 3 4 5; do
  CODE=$(cat "$TMP_DIR/race_code_$i")
  if [ "$CODE" = "201" ]; then
    OK_COUNT=$((OK_COUNT + 1))
  elif [ "$CODE" = "422" ]; then
    REJ_COUNT=$((REJ_COUNT + 1))
  else
    echo "FAIL: concurrent overdraft request #$i returned unexpected HTTP $CODE"
    exit 1
  fi
done
if [ "$OK_COUNT" -ne 3 ] || [ "$REJ_COUNT" -ne 2 ]; then
  echo "FAIL: expected 3x 201 and 2x 422 from 100 CHF balance with 5x 30 CHF requests, got 201=$OK_COUNT 422=$REJ_COUNT"
  exit 1
fi

INVARIANT_MISMATCH=$(psql_query "
  SELECT COUNT(*) FROM (
    SELECT a.id, a.balance, COALESCE(SUM(l.amount), 0) AS ledger_sum
    FROM accounts a
    LEFT JOIN ledger_entries l ON l.account_id = a.id
    WHERE a.id IN ('$TEST_SENDER', '$TEST_RECEIVER')
    GROUP BY a.id, a.balance
    HAVING a.balance < 0 OR a.available_balance < 0 OR a.balance <> COALESCE(SUM(l.amount), 0)
  ) t;
" | tr -d '[:space:]')
if [ "$INVARIANT_MISMATCH" != "0" ]; then
  echo "FAIL: balance/ledger invariant violated for test accounts"
  exit 1
fi
echo "OK: 3x 201, 2x 422, no negative balances, ledger sums match accounts.balance"

echo ""
echo "==> 5. Concurrency: simultaneous duplicate Idempotency-Key requests"
SHARED_KEY=$(new_uuid)
PIDS=()
for i in 1 2 3 4 5; do
  (
    RESP=$(curl -s -w "\n%{http_code}" \
      -X POST "$BASE_URL/api/v1/payments" \
      -H "Content-Type: application/json" \
      -H "Idempotency-Key: $SHARED_KEY" \
      -d "{\"amount\":\"5.00\",\"currency\":\"CHF\",\"senderAccountId\":\"$ALICE_ID\",\"receiverAccountId\":\"$BOB_ID\"}")
    echo "$RESP" | tail -1 > "$TMP_DIR/idem_code_$i"
    echo "$RESP" | sed '$d' > "$TMP_DIR/idem_body_$i"
  ) &
  PIDS+=($!)
done
for pid in "${PIDS[@]}"; do
  wait "$pid"
done

IDEM_IDS=()
for i in 1 2 3 4 5; do
  CODE=$(cat "$TMP_DIR/idem_code_$i")
  if [ "$CODE" != "200" ] && [ "$CODE" != "201" ]; then
    echo "FAIL: concurrent idempotent request #$i returned HTTP $CODE"
    cat "$TMP_DIR/idem_body_$i"
    exit 1
  fi
  PID_VAL=$(python3 -c "import sys, json; print(json.load(sys.stdin)['id'])" < "$TMP_DIR/idem_body_$i")
  IDEM_IDS+=("$PID_VAL")
done
UNIQUE_IDS=$(printf "%s\n" "${IDEM_IDS[@]}" | sort -u | wc -l | tr -d '[:space:]')
if [ "$UNIQUE_IDS" != "1" ]; then
  echo "FAIL: expected 1 unique payment id across concurrent duplicates, got $UNIQUE_IDS"
  exit 1
fi
WINNING_ID="${IDEM_IDS[0]}"
LEDGER_ROWS=$(psql_query "SELECT COUNT(*) FROM ledger_entries WHERE payment_id = '$WINNING_ID';" | tr -d '[:space:]')
if [ "$LEDGER_ROWS" != "2" ]; then
  echo "FAIL: expected 2 ledger rows for $WINNING_ID, got $LEDGER_ROWS"
  exit 1
fi
if ! NOTIFS=$(wait_for_notifications "$WINNING_ID" "PAYMENT_COMPLETED,PAYMENT_RESERVED"); then
  echo "FAIL: expected exactly 1 PAYMENT_RESERVED + 1 PAYMENT_COMPLETED in sent_notifications for $WINNING_ID, got '$NOTIFS'"
  exit 1
fi
echo "OK: concurrent idempotency produced 1 payment ($WINNING_ID), 2 ledger entries, and 2 deduplicated notifications"

echo ""
echo "==> 6. High Availability: payment intake while Kafka is down, then drain on recovery"
$COMPOSE stop kafka
KAFKA_STOPPED=1

HA_KEY=$(new_uuid)
HA_RESP=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $HA_KEY" \
  -d "{\"amount\":\"7.00\",\"currency\":\"CHF\",\"senderAccountId\":\"$ALICE_ID\",\"receiverAccountId\":\"$BOB_ID\"}")
HA_CODE=$(echo "$HA_RESP" | tail -1)
HA_BODY=$(echo "$HA_RESP" | sed '$d')
if [ "$HA_CODE" != "201" ]; then
  echo "FAIL: payment intake while Kafka is down expected 201, got $HA_CODE"
  echo "$HA_BODY"
  exit 1
fi
HA_ID=$(echo "$HA_BODY" | python3 -c "import sys, json; print(json.load(sys.stdin)['id'])")
echo "OK: payment $HA_ID accepted (201) while Kafka was stopped"

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

if ! NOTIFS=$(wait_for_notifications "$HA_ID" "PAYMENT_COMPLETED,PAYMENT_RESERVED"); then
  echo "FAIL: expected outbox drain & notifications for $HA_ID after Kafka recovery, got '$NOTIFS'"
  exit 1
fi
echo "OK: outbox drained and notifications delivered after Kafka recovery ($NOTIFS)"

echo ""
echo "All resilience smoke checks passed."
