# ADR-0004: Versioned JSON envelope vs Avro + Schema Registry

**Date**: 2024-01-01
**Status**: Accepted

## Context

Kafka events need a schema format. Avro with a Schema Registry provides compile-time schema evolution guarantees. A custom JSON envelope is simpler but requires consumers to handle versioning manually.

## Decision

Use a **versioned JSON envelope** (`EventEnvelope<T>`) with explicit `type` and `version` fields. Consumers reject events with unknown versions before deserialising the payload.

## Consequences

### Positive

- Zero additional infrastructure (no Schema Registry).
- Human-readable events simplify debugging.
- `version` field allows consumers to gate on schema changes explicitly.

### Negative

- No compile-time schema compatibility check between producers and consumers.
- Schema evolution requires coordinated deployments.

### Neutral

- Migration to Avro + Schema Registry is straightforward: replace `EventEnvelope<T>` with a generated Avro record and register the schema. The `type` / `version` routing logic in consumers remains the same.
