# Messaging

This document describes the Kafka messaging strategy, topic topology, event schemas, delivery guarantees, and failure-handling mechanisms in the Payment Service.

---

## 1. Messaging Strategy

- **REST intake, Kafka for notifications only** ([ADR-0009](adr/0009-rest-intake-kafka-notifications-only.md)): Payment creation (`POST /api/v1/payments`) is synchronous over HTTP. Kafka is not on the critical payment intake path; it is used solely to broadcast payment lifecycle events for downstream notification.
- **Transactional Outbox** ([ADR-0003](adr/0003-outbox-vs-cdc.md)): Every payment state change (`RESERVED`, `REJECTED`, `COMPLETED`, `FAILED`) writes an `EventEnvelope` row to `outbox_events` (`WriteOutboxTask` → `OutboxStore`) inside the **same PostgreSQL transaction** as the domain update. `OutboxRelay` polls `outbox_events` and publishes records to Kafka outside any database transaction.
- **Colocated notification consumer** ([ADR-0019](adr/0019-colocated-notification-module-and-shared-db.md)): `NotificationConsumer` runs in the same service deployment under its own consumer group (`app.notification.group-id`) and shares the PostgreSQL database for deduplication.

---

## 2. Topics, Producers, Consumers, Keys & Schemas

Topic partition and replication counts are configured via `app.kafka.topics` (`partitions: 6`, `replicas: 3` in `application.yml`) and auto-created by `KafkaConfig`.

| Topic | Producer | Consumer (Group ID) | Message Key | Headers | Schema |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `payment-notifications` | `OutboxRelay` (`KafkaTemplate<String, String>`) | `NotificationConsumer.onPaymentEvent` (`payment-notifications`) | `aggregate_id` (`paymentId`, UUID string) | `eventId` (UUID), `eventType` | Versioned JSON `EventEnvelope<PaymentPayload>` (`version: 1`) |
| `payment-notifications.DLT` | `DeadLetterPublishingRecoverer` (via `DefaultErrorHandler`) | `NotificationConsumer.onDeadLetter` (`payment-notifications-dlt`) | Original `paymentId` (preserved) | Spring Kafka DLT headers (exception, original topic/partition/offset) | Original `EventEnvelope<PaymentPayload>` JSON payload |

### Partitioning & Ordering

Using `paymentId` (`aggregate_id`) as the Kafka record key guarantees **partition affinity**: all lifecycle events for a given payment land on the same partition and are consumed in order.

### Event Schema ([ADR-0004](adr/0004-event-schema.md))

Events use a versioned JSON envelope (`EventEnvelope<T>`, `version = 1`) rather than Avro + Schema Registry:

```json
{
  "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "type": "PAYMENT_COMPLETED",
  "version": 1,
  "occurredAt": "2026-01-01T12:00:00Z",
  "correlationId": "a1b2c3d4-0000-0000-0000-000000000001",
  "payload": {
    "paymentId": "b1f5b8e0-7c3d-4a1e-9f2a-1234567890ab",
    "senderAccountId": "a0000000-0000-0000-0000-000000000001",
    "receiverAccountId": "a0000000-0000-0000-0000-000000000002",
    "receiverIban": null,
    "amount": "100.00",
    "currency": "CHF",
    "status": "COMPLETED",
    "failureReason": null
  }
}
```

- **Event types** (`PaymentEvents`): `PAYMENT_RESERVED`, `PAYMENT_REJECTED`, `PAYMENT_COMPLETED`, `PAYMENT_FAILED`.
- **`correlationId`**: Propagated from the HTTP request MDC (`X-Request-Id`, if it is a valid UUID); `null` otherwise.

---

## 3. Delivery Guarantees

The pipeline provides **at-least-once delivery** from database to Kafka and **idempotent consumption** (at-most-once notification side effect per `eventId`).

### Producer & Outbox (At-Least-Once)

- **Kafka Producer settings** (`application.yml`):
  - `acks: all` — broker acknowledges only after all in-sync replicas persist the record.
  - `enable.idempotence: true` with `retries: 2147483647`, `delivery.timeout.ms: 10000`, `request.timeout.ms: 5000`, `max.in.flight.requests.per.connection: 5` — prevents broker-level duplicates during producer retries.
- **Claim-then-send outbox relay** (`OutboxRelay`):
  - Runs every `app.outbox.relay-interval-ms` (`500 ms`), claiming up to `100` rows in TX1 using `FOR UPDATE SKIP LOCKED` (`claimed_at = now()`).
  - Sends each record to Kafka outside any DB transaction, waiting up to `app.outbox.ack-timeout-ms` (`15000 ms`).
  - Marks `published_at = now()` in TX2 after broker acknowledgment.
  - **Crash window**: If the process crashes after Kafka acks a record but before TX2 commits `published_at`, the row is reclaimed after `app.outbox.claim-ttl-ms` (`30000 ms`) and sent again. Consumers must therefore tolerate duplicate deliveries.

### Consumer (Idempotent Consumer — [ADR-0017](adr/0017-idempotent-notification-consumer.md))

- **Manual offset acknowledgment**: `enable-auto-commit: false` with `AckMode.RECORD`, `auto-offset-reset: earliest`.
- **Database deduplication**: `NotificationConsumer.onPaymentEvent` executes inside `@Transactional` and calls `SentNotificationStore.tryRecord(...)`:
  ```sql
  INSERT INTO sent_notifications (event_id, payment_id, account_id, event_type, message)
  VALUES (:eventId, :paymentId, :accountId, :eventType, :message)
  ON CONFLICT (event_id) DO NOTHING
  ```
  - **First delivery** (`1` row inserted): increments `payments.notifications.sent` and logs the notification.
  - **Duplicate delivery** (`0` rows inserted): increments `payments.notifications.duplicates` and skips without re-notifying, then acknowledges the offset.
  - **Failure**: rolls back the transaction so `sent_notifications` is not poisoned, allowing safe retry.

---

## 4. Failure Handling (Retries & Dead-Lettering)

### Outbox Relay Failures (Producer Side)

- If `kafkaTemplate.send(...).get(...)` fails or times out:
  - TX2 increments `publish_attempts`, stores the truncated exception message in `last_error` (max 500 chars), and resets `claimed_at = NULL`.
  - The current batch aborts immediately so the next scheduled cycle can retry in order.
- **Outbox dead-lettering**: After `MAX_PUBLISH_ATTEMPTS = 5` consecutive failures, `OutboxRelay` sets `dead_lettered_at = now()` (`V4__outbox_dead_lettered_at.sql`). Future relay cycles skip dead-lettered rows (`WHERE dead_lettered_at IS NULL`) so a single poison event cannot block subsequent events indefinitely.
- **Observability**: The `outbox.backlog.events` Micrometer gauge reports the count of unpublished outbox rows (`published_at IS NULL`).

### Consumer Retries & DLT (Consumer Side)

Configured in `KafkaConfig`:

- **Exponential backoff retries**: `DefaultErrorHandler` retries failed records with `ExponentialBackOff` (`app.kafka.consumer-retry.*`):
  - `initial-interval-ms`: `500`
  - `multiplier`: `2.0`
  - `max-attempts`: `3`
- **Non-retryable exceptions**: `IllegalArgumentException` and Jackson `StreamReadException` (e.g., malformed JSON or invalid `EventEnvelope` fields) bypass retries and route immediately to `payment-notifications.DLT`.
- **Dead-letter topic routing**: Once retries are exhausted (or skipped for non-retryable errors), `DeadLetterPublishingRecoverer` publishes the record to `payment-notifications.DLT` on the **same partition** as the source record (`cr.partition()`).
- **DLT monitoring**: `NotificationConsumer.onDeadLetter` consumes `payment-notifications.DLT`, logs an error with topic/key/offset, and increments the `payments.dlt.messages` counter.
