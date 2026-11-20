# Database Schema

The service uses PostgreSQL managed by Flyway migrations (`src/main/resources/db/migration/`). All monetary values use `NUMERIC(19,4)` and timestamps use `TIMESTAMPTZ`.

## `accounts`

Stores customer and clearing accounts along with their currency, total balance, and available (unheld) balance.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key identifying the account. |
| `owner_name` | `VARCHAR(100)` | Display name of the account owner (or clearing counterpart). |
| `account_type` | `VARCHAR(16)` | `CUSTOMER` (default) or `CLEARING` (counterpart for external payments). |
| `currency` | `CHAR(3)` | ISO 4217 3-letter currency code (e.g., `CHF`). |
| `balance` | `NUMERIC(19,4)` | Total ledger balance (`>= 0`). |
| `available_balance` | `NUMERIC(19,4)` | Spendable balance excluding active holds (`>= 0` and `<= balance`). |
| `version` | `BIGINT` | Optimistic locking version (`DEFAULT 0`). |
| `created_at` | `TIMESTAMPTZ` | Timestamp when the account was created. |
| `updated_at` | `TIMESTAMPTZ` | Timestamp of the last account update. |

## `payments`

Tracks payment lifecycle state, idempotency metadata, sender/receiver details, and settlement outcomes.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key identifying the payment. |
| `idempotency_key` | `VARCHAR(64)` | Unique client-supplied key (`uq_payments_idempotency_key`) used for request deduplication. |
| `request_hash` | `CHAR(64)` | SHA-256 hex digest of the normalized request body to detect key reuse with different payloads. |
| `sender_account_id` | `UUID` | Foreign key to `accounts(id)` for the debiting account (indexed via `ix_payments_sender`). |
| `receiver_type` | `VARCHAR(8)` | `INTERNAL` (account ID) or `EXTERNAL` (IBAN). |
| `receiver_account_id` | `UUID` | Foreign key to `accounts(id)` when `receiver_type = 'INTERNAL'`; `NULL` otherwise (cannot equal `sender_account_id`). |
| `receiver_iban` | `VARCHAR(34)` | Destination IBAN when `receiver_type = 'EXTERNAL'`; `NULL` otherwise. |
| `amount` | `NUMERIC(19,4)` | Payment amount (`> 0`). |
| `currency` | `CHAR(3)` | ISO 4217 currency code of the payment. |
| `status` | `VARCHAR(16)` | Current lifecycle state: `REJECTED`, `RESERVED`, `COMPLETED`, or `FAILED`. |
| `failure_reason` | `VARCHAR(50)` | Reason code when `REJECTED` or `FAILED` (e.g., `INSUFFICIENT_FUNDS`, `RAIL_REJECTED`). |
| `rail_reference` | `VARCHAR(64)` | Settlement reference returned by the internal or external rail. |
| `settlement_attempts` | `INT` | Number of settlement attempts recorded (`DEFAULT 0`). |
| `version` | `BIGINT` | Optimistic locking version (`DEFAULT 0`). |
| `reserved_at` | `TIMESTAMPTZ` | Timestamp when funds were held (partial index `ix_payments_reserved` where `status = 'RESERVED'`). |
| `finalized_at` | `TIMESTAMPTZ` | Timestamp when the payment reached `COMPLETED` or `FAILED`. |
| `created_at` | `TIMESTAMPTZ` | Timestamp when the payment row was created. |
| `updated_at` | `TIMESTAMPTZ` | Timestamp of the last payment update. |

## `ledger_entries`

Records immutable double-entry debit and credit postings (plus opening balances) per payment and account.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key identifying the ledger entry. |
| `payment_id` | `UUID` | Foreign key to `payments(id)`; `NULL` only for initial opening balance entries. |
| `account_id` | `UUID` | Foreign key to `accounts(id)` (indexed via `ix_ledger_account`; unique with `payment_id` via `uq_ledger_payment_account`). |
| `amount` | `NUMERIC(19,4)` | Signed posting amount (`<> 0`): negative for debits, positive for credits. |
| `created_at` | `TIMESTAMPTZ` | Timestamp when the ledger entry was recorded. |

## `outbox_events`

Stores transactional outbox domain events waiting to be claimed and published to Kafka.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key and unique `eventId` used by downstream consumers for deduplication. |
| `aggregate_id` | `UUID` | Payment ID used as the Kafka message key to preserve partition ordering. |
| `event_type` | `VARCHAR(40)` | Domain event type (e.g., `PAYMENT_RESERVED`, `PAYMENT_COMPLETED`, `PAYMENT_FAILED`, `PAYMENT_REJECTED`). |
| `payload` | `JSONB` | Serialized `EventEnvelope` JSON payload. |
| `created_at` | `TIMESTAMPTZ` | Timestamp when the outbox row was inserted (indexed via `ix_outbox_unpublished` where `published_at IS NULL`). |
| `claimed_at` | `TIMESTAMPTZ` | Timestamp when an `OutboxRelay` instance claimed the row for sending; reclaimed after `claim-ttl-ms` on crash. |
| `published_at` | `TIMESTAMPTZ` | Timestamp when Kafka acknowledged the event; `NULL` while pending. |
| `publish_attempts` | `INT` | Number of failed publish attempts (`DEFAULT 0`). |
| `last_error` | `VARCHAR(500)` | Truncated error message from the most recent failed publish attempt. |

## `sent_notifications`

Logs sender notifications and deduplicates redelivered Kafka events by `event_id`.

| Column | Type | Notes |
|---|---|---|
| `event_id` | `UUID` | Primary key matching the outbox `id`; duplicate deliveries conflict on this key and are skipped. |
| `payment_id` | `UUID` | Identifier of the payment associated with the notification. |
| `account_id` | `UUID` | Sender account ID receiving the notification. |
| `event_type` | `VARCHAR(40)` | Payment event type that triggered the notification. |
| `message` | `VARCHAR(500)` | Human-readable notification message. |
| `sent_at` | `TIMESTAMPTZ` | Timestamp when the notification was recorded. |

## `rail_sim_transfers`

Persists idempotent outcomes and references for simulated external rail settlements.

| Column | Type | Notes |
|---|---|---|
| `payment_id` | `UUID` | Primary key acting as the simulated rail's idempotency key. |
| `outcome` | `VARCHAR(16)` | Simulated rail result: `SETTLED` or `REJECTED`. |
| `reference` | `VARCHAR(64)` | Generated external rail reference string. |
| `created_at` | `TIMESTAMPTZ` | Timestamp when the simulated rail transfer was recorded. |

## `shedlock`

Coordinates distributed scheduled-job locks across service instances.

| Column | Type | Notes |
|---|---|---|
| `name` | `VARCHAR(64)` | Primary key identifying the scheduled task lock. |
| `lock_until` | `TIMESTAMP` | Timestamp until which the lock is held. |
| `locked_at` | `TIMESTAMP` | Timestamp when the lock was acquired. |
| `locked_by` | `VARCHAR(255)` | Identifier of the instance currently holding the lock. |
