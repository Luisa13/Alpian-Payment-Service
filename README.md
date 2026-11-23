# Payment Service

A production-grade Spring Boot payment microservice demonstrating banking-grade reliability patterns: two-phase reserve/settle/finalize execution, double-entry ledger accounting, transactional outbox delivery to Kafka, idempotent HTTP intake and event consumption, and automated recovery for uncertain settlement outcomes.

---

## What's inside

| Pattern | Implementation |
|---|---|
| Two-phase settlement | `CreatePaymentFlow` orchestrates `ReserveFundsFlow` (hold funds) → `SettlementRail` → `FinalizeSettlementFlow` (settle/release + double-entry ledger) |
| Double-entry ledger | Immutable `ledger_entries` with `UNIQUE (payment_id, account_id)` posted atomically on completion |
| Transactional outbox | `OutboxRelay` claims batches with `FOR UPDATE SKIP LOCKED`, publishes to Kafka outside DB transactions, and dead-letters after 5 failed attempts |
| HTTP idempotency | SHA-256 `request_hash` + `UNIQUE (idempotency_key)` on `payments` (replays return `200 OK`; mismatched payload returns `422`) |
| Consumer idempotency | `NotificationConsumer` deduplicates via `sent_notifications` (`PRIMARY KEY (event_id)`) with `ON CONFLICT DO NOTHING` |
| Stale-payment recovery | `RecoveryJob` polls uncertain `RESERVED` payments, checks rail status, and finalizes them idempotently |
| Contract-first API | OpenAPI 3.1 ([api.yaml](src/main/resources/openapi/api.yaml)) → `openapi-generator` → generated Spring interfaces (`PaymentsApi`, `AccountsApi`) |
| Structured logging | Logstash JSON encoder (non-local profiles) with `X-Request-Id` correlation ID in MDC |
| Observability | Micrometer metrics + Actuator probes (`/actuator/health/{liveness,readiness}`) + Prometheus & Grafana |
| Error model | RFC 7807 / 9457 `ProblemDetail` via `GlobalExceptionHandler` |
| Retry + DLT | `DefaultErrorHandler` with exponential backoff routing poison records to `payment-notifications.DLT` |

---

## Prerequisites

- Java 21
- Docker Desktop (or compatible daemon)
- `make`

---

## Quickstart

```bash
git clone <repo-url> payment-service
cd payment-service
make run       # builds the image via Jib and starts the app + Postgres, Kafka, Prometheus, Grafana
make smoke     # exercises POST /api/v1/payments, idempotent replay, conflict check, and metrics
```

To run only the infrastructure containers and start the app locally with `./gradlew bootRun`:

```bash
make up
./gradlew bootRun
```

Local UIs & endpoints:
- API: http://localhost:8080 (`POST /api/v1/payments`, `GET /api/v1/payments/{id}`, `GET /api/v1/accounts/{id}`)
- Health probes: http://localhost:8080/actuator/health/readiness
- Grafana: http://localhost:3000 (`admin` / `admin`)
- Kafka UI: http://localhost:8090
- Prometheus: http://localhost:9090

### Make workflow

| Target | Description |
|---|---|
| `make up` | Start infrastructure containers (PostgreSQL, Kafka, Kafka UI, Prometheus, Grafana, MkDocs) |
| `make run` | Build the `payment-service:latest` image with Jib and start the full stack (`--profile app`) |
| `make down` | Stop and remove containers (preserves PostgreSQL data volume) |
| `make build` | Run `./gradlew clean build` (OpenAPI codegen, Spotless check, unit/integration/E2E tests, JaCoCo coverage) |
| `make test` | Run `unitTest`, `integrationTest`, and `e2eTest` |
| `make format` | Auto-format Java sources with Spotless (`palantir-java-format`) |
| `make smoke` | Run core API & metrics smoke test against `http://localhost:8080` |
| `make smoke-outbox` | Verify outbox relay delivery to Kafka |
| `make smoke-outbox-dlq` | Verify outbox dead-lettering during a simulated Kafka outage |
| `make smoke-burst` | Run concurrent burst payments against the live service |
| `make smoke-resilience` | Verify concurrency, error handling, and external rail flows |

---

## Architecture overview

```
Client
  │  POST /api/v1/payments  (Idempotency-Key header)
  │  GET  /api/v1/payments/{id}
  │  GET  /api/v1/accounts/{id}
  ▼
PaymentController ──► CreatePaymentFlow
                        │
                        ├── 1. ReserveFundsFlow (TX1)
                        │        ├── lock sender & hold available_balance (accounts)
                        │        ├── insert payment = RESERVED (payments)
                        │        └── append PAYMENT_RESERVED (outbox_events)
                        │
                        ├── 2. SettlementRail (outside any DB transaction)
                        │        ├── InternalTransferRail   (internal receiverAccountId)
                        │        └── SimulatedExternalRail  (external receiverIban)
                        │
                        └── 3. FinalizeSettlementFlow (TX2)
                                 ├── SETTLED  → debit/credit + ledger_entries + COMPLETED + PAYMENT_COMPLETED
                                 ├── REJECTED → release hold + FAILED + PAYMENT_FAILED
                                 └── UNKNOWN  → leave RESERVED (returns 202 Accepted; retried by RecoveryJob)

outbox_events ──► OutboxRelay (@Scheduled) ──► Kafka (payment-notifications, key = paymentId)
                                                    │
                                                    ▼
                                          NotificationConsumer
                                            ├── dedupe via sent_notifications
                                            └── failures → payment-notifications.DLT
```

Every boundary crossing is at-least-once: the transactional outbox guarantees lifecycle events are published even if the service crashes after the database commit, and `NotificationConsumer` deduplicates deliveries by `eventId`.

---

## Documentation

Detailed architecture, API, database, messaging, testing docs, and ADRs live under [`docs/`](docs/index.md).

---

## Assumptions & Trade-offs

- **Polling outbox vs CDC**: Polling `outbox_events` every 500 ms with `FOR UPDATE SKIP LOCKED` requires no Debezium/Connect cluster at the cost of sub-second relay latency (see [ADR-0003](docs/adr/0003-outbox-vs-cdc.md)).
- **Spring Data JDBC / `JdbcClient` vs JPA**: Explicit SQL and aggregate boundaries avoid lazy-loading surprises and keep lock ordering deterministic (see [ADR-0002](docs/adr/0002-data-access-layer.md)).
- **Versioned JSON envelope vs Avro**: Zero-dependency JSON envelopes (`EventEnvelope<T>`) keep local and test setups lightweight; Avro + Schema Registry is the natural next step for multi-team schema governance (see [ADR-0004](docs/adr/0004-event-schema.md)).
- **Colocated notification consumer**: `NotificationConsumer` runs in the same deployable under its own Kafka consumer group and can be extracted into a separate service without changing the event contract (see [ADR-0019](docs/adr/0019-colocated-notification-module-and-shared-db.md)).
