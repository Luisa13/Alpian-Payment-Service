# ADR-0020: Asynchronous callback endpoint for external settlement rails (Phase 1.5)

**Date**: 2026-11-22
**Status**: Proposed

## Context

The current `SimulatedExternalRail` is synchronous—it returns `SETTLED`, `REJECTED`, or `UNKNOWN` inline (`ADR-0014`). Real-world external payment rails (such as SEPA, SWIFT, and card networks) do not operate this way: they accept a payment submission and push the final settlement outcome asynchronously at a later time.

## Decision

Introduce an asynchronous callback endpoint (Phase 1.5 — **not yet implemented**) to receive delayed settlement outcomes from external rails and drive the finalization of `RESERVED` payments (`COMPLETED` or `FAILED`).

## Consequences

### Positive

- Models real-world external rail protocols where settlement confirmation arrives asynchronously after initial submission.
- Enables timely finalization of pending or `UNKNOWN` payments without relying solely on periodic recovery jobs.

### Negative

- Introduces an inbound callback/webhook surface that will require authentication or signature verification, idempotent processing, and correlation with in-flight payments.

### Neutral

- Complements the three-outcome `SettlementRail` port (`ADR-0014`) and inline settlement with `202 Accepted` fallback (`ADR-0015`) without altering the core payment state machine (`ADR-0013`).
