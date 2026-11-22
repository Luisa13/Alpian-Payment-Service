# ADR-0019: Co-locate notification consumer as a module with Kafka as the logical boundary

**Date**: 2026-11-22
**Status**: Accepted

## Context

The payment service publishes domain events to Kafka via the transactional outbox (`ADR-0003`, `ADR-0009`) and consumes them in `NotificationConsumer` (`ADR-0017`). Running the notification consumer in a separate deployable service from day one adds operational overhead, while co-locating it in the same application raises concerns about hidden coupling if not bounded properly.

## Decision

Co-locate the notification system as a module inside the same Spring Boot application and deployment unit, while keeping the **Kafka topic** (not the JVM) as the logical service boundary:

- `NotificationConsumer` depends only on `application.events` (event types and constants) and nothing domain-internal.
- It maintains its own Kafka consumer group ID, its own deduplication table (`sent_notifications`), and its own metrics namespace.
- If extracted into a standalone service later, the Kafka contract (topic name, message format, consumer group) remains 100% unchanged.
- **Explicitly acknowledged trade-off**: `sent_notifications` lives in the same PostgreSQL database as `payments`, `accounts`, and `outbox_events`.

## Consequences

### Positive

- Operational simplicity: a single deployment unit, CI/CD pipeline, and runtime configuration without introducing hidden domain coupling.
- Extracting the notification module into its own service requires no application-level refactoring of the event contract or payment domain.

### Negative

- **Shared database coupling**: Because `sent_notifications` resides in the same PostgreSQL database as `payments`, `accounts`, and `outbox_events`, extracting the notification consumer into a separate service also requires migrating `sent_notifications` to its own database (or temporarily accepting cross-service database coupling). This shared database—not the JVM co-location—is the real hidden cost of the current setup.
- Shares JVM resources and process lifecycle with the core payment service.

### Neutral

- Co-location is an operational decision, not a logical one; the messaging boundary is already cleanly separated.
