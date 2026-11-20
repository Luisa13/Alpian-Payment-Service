# ADR-0006: Light hexagonal architecture with Flows and Tasks

**Date**: 2024-01-01
**Status**: Accepted

## Context

Standard hexagonal architecture (ports and adapters) isolates domain logic from infrastructure but often introduces excessive boilerplate (separate port interfaces, repetitive DTO mapping, and ceremony) for microservices with focused domain scope. Furthermore, monolithic application service classes frequently become bloated coordinators mixing persistence, business rules, rail calls, and transaction boundaries.

## Decision

Adopt a **light version of hexagonal architecture** with a specialized **Command pattern (Flows and Tasks)** in the application layer:
- **Dependency boundaries**: Inward dependencies (`api` and `infrastructure` depend on `domain` and `application`; `domain` has zero dependencies on outer layers or Spring runtime). Repositories and rail interfaces live in `domain` and are directly implemented in `infrastructure` without intermediate adapter modules.
- **Flows and Tasks**: The application layer decomposes use cases into atomic, single-responsibility `Task<A, R>` components composed by orchestrating `Flow` beans. Flows define transaction boundaries (e.g. `ReserveFundsFlow`, `FinalizeSettlementFlow`), while tasks never manage transactions (`@Transactional` forbidden on tasks).

## Consequences

### Positive

- Clean separation of concerns with significantly less boilerplate than full ports-and-adapters.
- Reusable, independently unit-testable tasks with single responsibilities.
- Explicit transaction and retry boundaries controlled exclusively at the flow orchestrator level (enforced by ArchUnit).

### Negative

- More classes per use case compared to a single traditional application service class.
- Flow composition must be carefully organized to keep orchestration logic discoverable.

### Neutral

- Enforced structurally via `ArchitectureTest` (`ArchUnit`), preventing architecture erosion over time.
