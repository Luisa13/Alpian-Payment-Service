# ADR-0001: Use Spring Boot 3.5.x

**Date**: 2024-01-01
**Status**: Accepted

## Context

The project requires Java 21 LTS features (virtual threads GA) and a stable ecosystem of libraries: openapi-generator, ArchUnit, Testcontainers 2.x, Micrometer. Spring Boot 4.x (targeting Spring Framework 7 / Jakarta EE 11) was evaluated but its ecosystem compatibility with these libraries was unverified at project start.

## Decision

Use **Spring Boot 3.5.x** (latest 3.x LTS-aligned release).

## Consequences

### Positive

- Full ecosystem compatibility: openapi-generator 7.x, ArchUnit 1.x, Testcontainers 2.x, Micrometer all work out of the box.
- Virtual threads available via `spring.threads.virtual.enabled=true` with no code changes.
- Jakarta EE 10 (not 11) — wider library support.

### Negative

- Must migrate to Boot 4.x when Jakarta EE 11 ecosystem matures.

### Neutral

- Spring Boot 3.5.x reaches end-of-support in late 2026; plan migration window accordingly.
