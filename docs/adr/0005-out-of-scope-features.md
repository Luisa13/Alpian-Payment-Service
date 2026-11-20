# ADR-0005: Exclude refunds, cancellations, and account creation from service scope

**Date**: 2024-01-01
**Status**: Accepted

## Context

A payment service can easily accumulate adjacent responsibilities such as customer account lifecycle management (creation, onboarding, KYC), in-flight payment cancellations, and post-settlement refund handling. In a banking platform, these capabilities involve separate regulatory, accounting, and reversal rails. Attempting to support them inside this service increases aggregate complexity, blurs bounded contexts, and dilutes focus from core high-reliability payment execution.

## Decision

Explicitly declare **refunds, cancellations, and account creation as out of scope** for this service.
- **Account creation**: Managed upstream by a dedicated Account / Core Banking service; accounts are treated as external entities whose balances are referenced and updated during payments.
- **Cancellations and refunds**: Managed by separate dispute/reversal services or future saga orchestrators via compensation events rather than direct mutations in this service.

## Consequences

### Positive

- Keeps the service focused strictly on high-integrity payment execution (initiation, reservation, settlement, idempotency).
- Prevents domain boundary erosion between Account Lifecycle Management and Payment Processing.
- Simplifies domain aggregates (`Payment`, `Account`), state machine transitions, and database schema.

### Negative

- Clients cannot trigger payment cancellations or refunds directly through this service's API.
- Integration tests and local development must seed existing account records rather than provisioning them via API.

### Neutral

- Reversals and refunds can be integrated in the future via asynchronous compensation sagas reacting to payment domain events published to Kafka.
