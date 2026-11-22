# ADR-0021: Recovery job status polling vs. idempotent re-submission

**Date**: 2026-11-22
**Status**: Accepted

## Context

When `settle()` returns `UNKNOWN` (for example, due to a timeout), the payment remains in `RESERVED` (`ADR-0014`) and must be resolved by a recovery job. Conceptually, a payment service's recovery job should check the payment's status with the payment provider—polling for a lost callback or timed-out outcome—rather than blindly re-submitting the payment.

## Decision

Include status-checking / polling in the recovery job flow so that recovery is conceptually accurate for real-world payment providers, while noting that re-submission is already safe in the current adapters due to built-in idempotency:

- **`SimulatedExternalRail`**: Stores its outcome per payment ID in `rail_sim_transfers`. If a previous `settle()` returned `UNKNOWN` (timeout), nothing was stored in `rail_sim_transfers`, so the next call generates a fresh random outcome (a new attempt); if an outcome was stored, idempotency returns that stored outcome.
- **`InternalTransferRail`**: Always returns `SETTLED` idempotently.

Adding status checking is a small change that makes the recovery job conceptually correct for providers that support status queries after a lost callback or timeout.

## Consequences

### Positive

- Aligns the recovery job design with real-world payment provider semantics (querying status for a lost callback or timed-out request before or instead of re-submitting).
- Retains safety through built-in idempotency in both `SimulatedExternalRail` (`rail_sim_transfers`) and `InternalTransferRail`.

### Negative

- Slightly expands the rail port/adapter surface to support status checking in addition to `settle()`.

### Neutral

- Under `SimulatedExternalRail`, a prior `UNKNOWN` timeout leaves no persisted row in `rail_sim_transfers`, so recovery still triggers a fresh attempt when no outcome was recorded.
