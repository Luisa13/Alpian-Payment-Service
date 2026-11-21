# ADR-0013: Payment state machine with guarded transitions

**Date**: 2026-11-21
**Status**: Accepted

## Context

Payments move through distinct lifecycle stages across multiple transactions (`ReserveFundsFlow`, `FinalizeSettlementFlow`, and future recovery/callback paths). Allowing arbitrary status overwrites could corrupt financial state—for example, failing an already `COMPLETED` payment or double-releasing a hold.

## Decision

Model the payment lifecycle in `PaymentStatus` as an explicit state machine with guarded transitions enforced by `Payment` and `TransitionPaymentTask`:

- **Initial states** (`canStartAs()`): `REJECTED` (terminal, no hold) or `RESERVED` (funds held).
- **Allowed transitions** (`canTransitionTo(next)`): only `RESERVED -> COMPLETED` and `RESERVED -> FAILED`.
- **Terminal states** (`isFinal()`): `REJECTED`, `COMPLETED`, and `FAILED` never transition further; invalid transitions throw `InvalidPaymentStateException`.

## Consequences

### Positive

- Invalid state transitions fail fast in the domain layer before any database update or ledger posting.
- Terminal states are immutable, making finalization and replay logic safe to reason about.

### Negative

- Any new lifecycle state (such as `SETTLING` in Phase 2) requires updating the enum transition rules and database constraints together.

### Neutral

- `PaymentStatus` currently models the Phase 1 lifecycle; `SETTLING` is deferred to Phase 2.
