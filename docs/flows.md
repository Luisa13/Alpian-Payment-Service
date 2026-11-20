# Flows

This document describes the core application and messaging flows in the Payment Service.

---

## 1. Create Payment Flow

`CreatePaymentFlow` orchestrates payment intake and synchronous settlement inside a single HTTP request (`POST /api/v1/payments`) while keeping database transactions short and never holding a DB lock across a rail call:

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant API as PaymentController
    participant CPF as CreatePaymentFlow
    participant RFF as ReserveFundsFlow (TX1)
    participant RAIL as SettlementRail
    participant FSF as FinalizeSettlementFlow (TX2)
    participant DB as PostgreSQL

    C->>API: POST /api/v1/payments (Idempotency-Key)
    API->>CPF: initiate(args)
    CPF->>RFF: reserve(args)
    RFF->>DB: Check idempotency & lock sender account
    alt Insufficient funds
        RFF->>DB: INSERT payments (REJECTED) + outbox_events
        RFF-->>CPF: Result.Rejected
        CPF-->>API: PaymentResult.REJECTED
        API-->>C: 422 Unprocessable Entity
    else Sufficient funds
        RFF->>DB: Hold funds, INSERT payments (RESERVED) + outbox_events
        RFF-->>CPF: Result.Reserved
        CPF->>RAIL: settle(payment) [no DB transaction]
        RAIL-->>CPF: SettlementOutcome (SETTLED / REJECTED / UNKNOWN)
        CPF->>FSF: finalizeSettlement(paymentId, outcome)
        alt outcome == SETTLED
            FSF->>DB: Update payment COMPLETED, lock & update accounts, post ledger, write outbox
            FSF-->>CPF: Payment (COMPLETED)
            CPF-->>API: PaymentResult.CREATED
            API-->>C: 201 Created
        else outcome == REJECTED
            FSF->>DB: Update payment FAILED, release hold, write outbox
            FSF-->>CPF: Payment (FAILED)
            CPF-->>API: PaymentResult.CREATED
            API-->>C: 201 Created
        else outcome == UNKNOWN
            FSF->>DB: Increment settlement_attempts (stays RESERVED)
            FSF-->>CPF: Payment (RESERVED)
            CPF-->>API: PaymentResult.ACCEPTED
            API-->>C: 202 Accepted
        end
    end
```

---

## 2. Outbox Relay Flow

`OutboxRelay` implements the **claim-then-send** transactional outbox pattern on a fixed schedule (`app.outbox.relay-interval-ms`):

```mermaid
sequenceDiagram
    autonumber
    participant SCHED as OutboxRelay (@Scheduled)
    participant DB as PostgreSQL (outbox_events)
    participant K as Kafka (payment-notifications)

    SCHED->>DB: TX1: SELECT ... WHERE published_at IS NULL AND (claimed_at IS NULL OR expired) FOR UPDATE SKIP LOCKED
    SCHED->>DB: TX1: UPDATE outbox_events SET claimed_at = now()
    DB-->>SCHED: Claimed batch (up to 100 rows)

    loop For each row in batch
        SCHED->>K: Send ProducerRecord (key=aggregate_id, headers=eventId,eventType)
        alt Broker acks within ack-timeout-ms
            K-->>SCHED: Ack
            SCHED->>DB: TX2: UPDATE outbox_events SET published_at = now()
        else Send fails or times out
            K-->>SCHED: Error / Timeout
            SCHED->>DB: TX2: UPDATE publish_attempts + 1, last_error, claimed_at = NULL
            Note over SCHED: Abort batch; retry on next cycle
        end
    end
```

---

## 3. Notification Flow

`NotificationConsumer` consumes payment lifecycle events from Kafka and sends idempotent notifications to payment senders:

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka (payment-notifications)
    participant NC as NotificationConsumer
    participant DB as PostgreSQL (sent_notifications)
    participant DLT as Kafka (payment-notifications.DLT)

    K->>NC: Deliver ConsumerRecord (EventEnvelope)
    NC->>NC: Deserialize payload & format message
    NC->>DB: TX: INSERT INTO sent_notifications (event_id, ...) ON CONFLICT DO NOTHING
    alt First delivery
        DB-->>NC: Inserted (true)
        NC->>NC: Increment payments.notifications.sent & log notification
        NC-->>K: Ack offset (AckMode.RECORD)
    else Duplicate delivery
        DB-->>NC: Already exists (false)
        NC->>NC: Increment payments.notifications.duplicates & skip
        NC-->>K: Ack offset
    else Processing error
        NC--xDB: Rollback TX
        Note over K,NC: ErrorHandler retries with exponential backoff
        K->>DLT: Publish to payment-notifications.DLT after max attempts
        DLT->>NC: onDeadLetter() -> increment payments.dlt.messages
    end
```
