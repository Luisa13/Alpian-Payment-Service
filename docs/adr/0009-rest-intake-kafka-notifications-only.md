# ADR-0009: REST intake, Kafka outbound for notifications only

**Date**: 2026-11-21
**Status**: Accepted

## Context

The payment service must expose a synchronous API for clients initiating transfers and querying payment/account state, while also notifying downstream consumers of payment state changes. Using Kafka as the primary command/intake channel would force callers into an asynchronous request-reply pattern and complicate immediate validation, idempotency feedback, and HTTP status reporting.

## Decision

Use **REST over HTTP** (`PaymentController`, `AccountController`, defined in `openapi/api.yaml`) as the sole intake boundary for commands and queries, and use **Apache Kafka** strictly as an outbound event channel for payment state-change notifications (`payment-notifications` and `payment-notifications.DLT`).

## Consequences

### Positive

- Callers receive immediate synchronous feedback for validation errors, idempotency conflicts, rejections, and fast-path settlements.
- Clear boundary: HTTP handles request/response semantics and idempotency keys; Kafka handles decoupled fan-out of domain notifications.
- Simpler operational model—no inbound Kafka command consumer or dead-letter handling for malformed client commands.

### Negative

- Clients must wait for the HTTP call (or poll `GET /v1/payments/{id}` after a `202 Accepted`) rather than firing-and-forgetting a command onto a topic.

### Neutral

- Notification delivery is at-least-once via the transactional outbox (`ADR-0003`) and `NotificationConsumer`.
