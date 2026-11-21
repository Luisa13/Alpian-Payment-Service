# ADR-0016: Double-entry ledger written on completion

**Date**: 2026-11-21
**Status**: Accepted

## Context

Account balances (`balance`, `held_balance`) represent current mutable state, which is insufficient on its own for financial auditing, reconciliation, and proving that funds were neither created nor destroyed across transfers.

## Decision

Write immutable **double-entry ledger** rows (`PostLedgerEntriesTask` -> `LedgerRepository`) inside the same database transaction (`FinalizeSettlementFlow`) when a payment transitions to `COMPLETED`:

- One **debit** entry for the sender account (`postDebit`).
- One **credit** entry for the receiver account (`postCredit`, using the external clearing/settlement account for external receivers).
- Enforce `CONSTRAINT uq_ledger_payment_account UNIQUE (payment_id, account_id)` in `ledger_entries` so a payment can post at most once per account.

## Consequences

### Positive

- Every completed payment has a balanced, immutable audit trail committed atomically with the payment status and balance updates.
- Database unique constraint prevents duplicate ledger postings if finalization is retried.

### Negative

- Adds two `INSERT` statements to the completion transaction.

### Neutral

- Rejected and failed payments do not write ledger entries since no funds permanently moved.
