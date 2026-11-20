# ADR-0007: ProblemDetail (RFC 7807) for API error handling

**Date**: 2024-01-01
**Status**: Accepted

## Context

REST APIs often suffer from the "Envelope" anti-pattern, wrapping responses in bespoke JSON payloads (`{ "success": false, "data": null, "error": { ... } }`) or returning inconsistent HTTP error shapes. This defeats standard HTTP status code semantics, breaks HTTP caching/client error interceptors, and forces clients to implement custom parsing logic.

## Decision

Use Spring's **`ProblemDetail` (RFC 7807 / RFC 9457)** as the standard error response format across the entire HTTP API:
- Centralized via `GlobalExceptionHandler` mapping all application and domain exceptions to `ProblemDetail`.
- Standard payload augmented with an explicit, machine-readable **`code`** enum (e.g. `PAYMENT_REJECTED`, `IDEMPOTENCY_CONFLICT`, `VALIDATION_FAILED`) and custom extension properties: **`paymentId`**, **`violations`** (field-level map), and **`errorReference`** (unique correlation ID for 5xx errors).

## Consequences

### Positive

- Eliminates the envelope anti-pattern; respects native HTTP status codes and standard client error handling.
- Deterministic, machine-readable client error routing via the explicit `code` enum rather than fragile HTTP status guessing or string parsing.
- Rich contextual error metadata (`violations`, `paymentId`, `errorReference`) without custom wrapper frameworks.
- Out-of-the-box support in Spring Boot 3.x and seamless alignment with OpenAPI 3.1 schema generation.

### Negative

- Clients must support standard RFC 7807 payloads or parse top-level JSON fields (`type`, `title`, `status`, `detail`).

### Neutral

- Internal server errors (500) hide internal stack traces from clients while attaching an `errorReference` logged on the server for log correlation.
