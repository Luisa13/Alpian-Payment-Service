# ADR-0003: Polling outbox relay vs Debezium CDC

**Date**: 2024-01-01
**Status**: Accepted

## Context

Publishing Kafka events reliably after a DB commit requires either (a) a polling relay that reads an outbox table or (b) change-data capture (CDC) streaming from the PostgreSQL WAL via Debezium.

## Decision

Use a **polling outbox relay** (`OutboxRelay`) with `SELECT … FOR UPDATE SKIP LOCKED` and a 1-second poll interval (100 ms in tests).

## Consequences

### Positive

- No additional infrastructure: no Kafka Connect cluster, no Debezium connector.
- Simple to reason about; easy to test with Testcontainers.
- `SKIP LOCKED` allows horizontal scaling of the relay without double-publishing.

### Negative

- Adds ~1 s end-to-end latency (poll interval).
- Polling under low load creates minor DB load (one query per second per replica).

### Neutral

- Debezium CDC would eliminate polling latency and DB load, but adds connector management, schema evolution complexity, and a Kafka Connect cluster.
