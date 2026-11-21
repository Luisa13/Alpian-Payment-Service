# ADR-0017: Idempotent notification consumer via sent_notifications deduplication

**Date**: 2026-11-21
**Status**: Accepted

## Context

The transactional outbox and polling relay (`ADR-0003`) provide **at-least-once** delivery of payment events to Kafka. Combined with Kafka consumer rebalances, retries, and offset-commit races, `NotificationConsumer` can receive the same `EventEnvelope` (`eventId`) more than once. Without consumer-side deduplication, redeliveries would send duplicate notifications to senders.

## Decision

Make `NotificationConsumer` idempotent using a PostgreSQL deduplication table (`sent_notifications` with `event_id UUID PRIMARY KEY`) and transactional processing:

1. Inside `@Transactional`, `SentNotificationStore.tryRecord(...)` executes `INSERT INTO sent_notifications (...) ON CONFLICT (event_id) DO NOTHING`.
2. If the insert affects 1 row (`true`), the notification is sent/logged; if 0 rows (`false`), the event is treated as a duplicate, counted via `payments.notifications.duplicates`, and skipped.
3. On transient processing failure, the transaction rolls back (so `sent_notifications` is not committed), the error handler retries with backoff, and exhausted records are published to `payment-notifications.DLT`.

## Consequences

### Positive

- Guarantees at-most-once notification side effects per `event_id` under at-least-once Kafka and outbox delivery.
- Simple, atomic deduplication using PostgreSQL's primary key conflict handling—no distributed cache or Kafka transactions required.
- Rollback on failure ensures failed attempts do not poison the deduplication table.

### Negative

- `sent_notifications` grows linearly with all processed events unless periodically archived or pruned.
- Requires one database `INSERT` per consumed notification event.

### Neutral

- Complements `ADR-0003` (outbox producer/relay) by closing the loop on the consumer side of at-least-once delivery.
