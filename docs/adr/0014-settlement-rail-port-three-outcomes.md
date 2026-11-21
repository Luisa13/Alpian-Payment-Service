# ADR-0014: Settlement rail as a port with three outcomes (UNKNOWN never fails)

**Date**: 2026-11-21
**Status**: Accepted

## Context

The payment service must support both internal transfers and external payment rails without coupling application orchestration to a specific rail protocol. Furthermore, when an external rail times out or returns an ambiguous error, treating that error as a definite failure and releasing the sender's hold would risk losing money if the rail actually settled the transfer.

## Decision

Define `SettlementRail` as a domain port returning `SettlementOutcome` with exactly three statuses:

- **`SETTLED`**: Money definitely moved (includes the rail's `reference`); finalize as `COMPLETED`.
- **`REJECTED`**: Definite refusal by the rail; release the hold and finalize as `FAILED`.
- **`UNKNOWN`**: Timeout, transport error, or ambiguous response. **Never fail or release the hold on `UNKNOWN`**; leave the payment `RESERVED`, record the attempt (`RecordSettlementAttemptTask`), and let recovery resolve it.

## Consequences

### Positive

- Decouples `CreatePaymentFlow` and `FinalizeSettlementFlow` from rail implementations (`InternalTransferRail`, `SimulatedExternalRail`).
- Eliminates the risk of releasing held funds when an external rail's state is uncertain.

### Negative

- `UNKNOWN` outcomes leave the payment in `RESERVED` with funds held until recovery or a later callback resolves the outcome.

### Neutral

- Any unexpected exception from a rail implementation must be translated to `UNKNOWN` rather than propagated as a rejection.
