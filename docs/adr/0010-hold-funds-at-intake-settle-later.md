# ADR-0010: Hold funds at intake, settle later (no locks during rail calls)

**Date**: 2026-11-21
**Status**: Accepted

## Context

Calling an external settlement rail is a network I/O operation with unpredictable latency and failure modes. Holding a database transaction and row-level locks open across the rail call would exhaust connection pools, block concurrent payments for the same account, and risk lock timeouts or deadlocks.

## Decision

Split payment execution in `CreatePaymentFlow` into three separate units of work outside any enclosing transaction:

1. **Reserve (`ReserveFundsFlow`, tx1)**: Validate, lock the sender row, hold `amount` (`available` -> `held`), and persist the payment in `RESERVED` state. Commit and release locks.
2. **Call rail (`CallSettlementRailTask`, no transaction)**: Invoke `SettlementRail.settle(...)` with a timeout and zero database locks held.
3. **Finalize (`FinalizeSettlementFlow`, tx2)**: Based on the outcome, either complete the transfer (consume hold, credit internal receiver if applicable, post ledger) or release the hold (`FAILED`), or record the attempt (`UNKNOWN`).

## Consequences

### Positive

- Database connections and row locks are held only for short, bounded local transactions.
- Concurrent payments from the same sender do not stall behind slow external rail calls.
- Funds are safely ring-fenced (`held_balance`) as soon as the payment is accepted.

### Negative

- Requires a two-phase balance model (`balance` / `available` vs `held_balance`) and compensation/recovery logic for `RESERVED` payments whose rail call or finalization did not complete.

### Neutral

- Internal transfers follow the same three-step flow for uniformity, even though their rail call is in-process.
