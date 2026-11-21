# ADR-0015: Inline settlement attempt with a 202 Accepted fallback (Phase 1)

**Date**: 2026-11-21
**Status**: Accepted

## Context

Always returning `202 Accepted` forces every client—even for fast internal transfers or responsive external rails—to poll for completion. Conversely, blocking indefinitely until settlement completes hurts API availability when a rail is slow or returns `UNKNOWN`.

## Decision

In Phase 1, attempt settlement **inline** within `CreatePaymentFlow.initiate(...)` (with a bounded timeout on `CallSettlementRailTask`) and map the resulting state in `PaymentController`:

- **`201 Created`**: Settlement reached a terminal state (`COMPLETED` or `FAILED`) within the request.
- **`202 Accepted`** (with `Location: /v1/payments/{id}`): Settlement returned `UNKNOWN` or finalization failed transiently, leaving the payment in `RESERVED`.
- **`200 OK`**: Idempotent replay of an existing payment.

## Consequences

### Positive

- Fast-path transfers complete in a single HTTP round-trip (`201 Created`) without client polling.
- Slow or ambiguous rail calls degrade gracefully to `202 Accepted` while funds remain safely held.

### Negative

- Request latency includes the bounded rail call timeout on the happy path; high-latency rails will more frequently hit the `202` fallback.

### Neutral

- Callers must handle both `201 Created` and `202 Accepted` (polling `GET /v1/payments/{id}` via the `Location` header).
