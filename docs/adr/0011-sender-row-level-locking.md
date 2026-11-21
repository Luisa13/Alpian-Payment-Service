# ADR-0011: Row-level locking on the sender to prevent double spending

**Date**: 2026-11-21
**Status**: Accepted

## Context

Two concurrent payment requests for the same sender account can pass an optimistic balance check simultaneously and both reserve funds, resulting in a negative available balance (double spending). Optimistic locking alone would force frequent client-visible retries under contention on active accounts.

## Decision

Acquire a pessimistic row-level lock (`SELECT ... FOR UPDATE` via `LockSenderAccountTask` / `AccountRepository.findByIdForUpdate`) on the **sender account row** during `ReserveFundsFlow` before checking available balance and placing the hold. When locking multiple accounts during finalization (`LockAccountsTask`), acquire locks in deterministic UUID order and retry transient lock timeouts/deadlocks via `TransactionRetry`.

## Consequences

### Positive

- Strictly serializes concurrent balance checks and holds for the same sender, preventing double spending at the database level.
- Payments for different senders proceed in parallel without contention.
- Deterministic lock ordering during finalization prevents deadlocks on bilateral transfers.

### Negative

- Concurrent requests for the *same* sender serialize on that row during tx1 (mitigated by keeping tx1 short and free of external I/O, per ADR-0010).

### Neutral

- Relies on PostgreSQL row-level `FOR UPDATE` semantics; `TransactionRetry` handles transient serialization/lock failures transparently.
