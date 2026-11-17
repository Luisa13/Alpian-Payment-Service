# ADR-0002: Spring Data JDBC for aggregates, JdbcClient for outbox

**Date**: 2024-01-01
**Status**: Accepted

## Context

The service needs a data access strategy that enforces DDD aggregate boundaries and supports `SELECT … FOR UPDATE SKIP LOCKED` for the outbox relay.

## Decision

Use **Spring Data JDBC** (`CrudRepository`) for aggregate roots (`Transfer`). Use **Spring's `JdbcClient`** directly for the outbox relay query (which requires `FOR UPDATE SKIP LOCKED`) and other non-aggregate queries.

## Consequences

### Positive

- Spring Data JDBC forbids cross-aggregate joins at the ORM level, enforcing bounded contexts.
- No lazy-loading surprises (unlike JPA).
- `JdbcClient` gives full SQL control for the skip-locked query.
- `@Version` on `Transfer` provides optimistic locking without JPA overhead.

### Negative

- More verbose mapping for complex object graphs compared to JPA.
- No JPQL / Criteria API; raw SQL required for non-trivial queries.

### Neutral

- Two data access abstractions in the same service (JDBC + JdbcClient) — intentional separation of concerns.
