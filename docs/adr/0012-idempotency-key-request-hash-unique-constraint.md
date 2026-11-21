# ADR-0012: Idempotency-Key with request hash and unique constraint

**Date**: 2026-11-21
**Status**: Accepted

## Context

Network retries and client timeouts can cause the same `POST /v1/payments` request to arrive multiple times. Without server-side idempotency, retries would create duplicate payments and double-charge the sender. Conversely, reusing an `Idempotency-Key` with a different payload must be rejected rather than silently replaying a mismatched payment.

## Decision

Require an `Idempotency-Key` header on payment creation, compute a deterministic SHA-256 `RequestHash` over `(senderAccountId, receiver, amount)`, and enforce uniqueness in PostgreSQL with `CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key)` on the `payments` table:

- If the key already exists with a **matching** `request_hash`, replay the stored payment (`200 OK` or rejection).
- If the key already exists with a **different** `request_hash`, throw `IdempotencyConflictException` (`409 Conflict` / `IDEMPOTENCY_CONFLICT`).
- Concurrent races that bypass the initial read and hit the unique constraint (`DuplicateIdempotencyKeyException`) are caught in `CreatePaymentFlow` and resolved by replaying/checking the winning row.

## Consequences

### Positive

- Guarantees at-most-once payment creation per `Idempotency-Key`, even under concurrent duplicate requests.
- Protects clients from accidental key reuse across different transfer payloads.

### Negative

- Rejected payments that were persisted with an idempotency key also consume that key; clients must use a fresh key for a corrected request.

### Neutral

- Idempotency records live directly on the `payments` table rather than a separate idempotency table, keeping lookup and insert atomic in one statement.
