# Payment Service Template

A production-grade Spring Boot microservice template for a Swiss digital bank, demonstrating banking-grade reliability patterns out of the box.

> **This is a template.** The `transfers` package is a working sample feature — replace it entirely with your domain. The surrounding infrastructure (outbox, idempotency, Kafka wiring, observability) is designed to be reused as-is.

---

## What's inside

| Pattern | Implementation |
|---|---|
| Transactional outbox | `OutboxRelay` polls with `SELECT … FOR UPDATE SKIP LOCKED` |
| HTTP idempotency | SHA-256 request hash + UNIQUE constraint on `idempotency_keys` |
| Consumer idempotency | `processed_events` table with UNIQUE on `event_id` |
| Optimistic locking | Spring Data JDBC `@Version` on aggregates |
| DDD aggregates | `Transfer` with sealed `TransferStatus` interface |
| Contract-first API | OpenAPI 3.1 → `openapi-generator` → generated Spring interfaces |
| Structured logging | Logstash JSON encoder; correlation ID in every log line |
| Observability | Micrometer counters + gauge; Grafana dashboard provisioned |
| Error model | RFC 9457 `ProblemDetail` with field-level validation errors |
| Retry + DLT | `DefaultErrorHandler` with exponential backoff; dead-letter topic |

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
make up        # starts Postgres, Kafka, Prometheus, Grafana
make smoke     # exercises the HTTP API and prints metrics
```

Grafana: http://localhost:3000 (admin / admin)
Kafka UI: http://localhost:8090
Prometheus: http://localhost:9090

---

## Architecture overview

```
Client
  │  POST /v1/transfers  (Idempotency-Key header)
  ▼
TransferController  ──►  TransferService  ──►  transfers table
                                          └──►  outbox_events table
                                                      │
                                              OutboxRelay (@Scheduled)
                                                      │  transfers.requested
                                                      ▼
                                          TransferEventConsumer
                                                      │  settle + write outbox
                                                      │  transfers.settled
                                                      ▼
                                              downstream consumers
```

Each arrow that crosses a service boundary is at-least-once: the outbox guarantees the event is published even if the app crashes after the DB commit.

---

## How to replace the sample feature

1. Delete `src/main/java/ch/alpian/payment/transfer/` and its tests.
2. Run `./gradlew build` — only compile errors from deleted references remain.
3. Add your own aggregate under `<your-domain>/domain/`, application service under `<your-domain>/application/`, and Kafka consumer under `<your-domain>/infrastructure/`.
4. Reuse `OutboxEntry` / `OutboxRelay`, `ProcessedEventStore`, `IdempotencyStore`, `EventEnvelope`, `CorrelationIdFilter`, `KafkaConfig`, and `GlobalExceptionHandler` unchanged.
5. Update `openapi/api.yaml` with your contract and re-run `./gradlew openApiGenerate`.

---

## Assumptions

- Single-region deployment; no cross-region replication concerns in scope.
- Kafka topics are pre-created (via `init-kafka` in docker-compose or an operator in K8s).
- Secret rotation is out of scope; GCP Secret Manager is the recommended solution.

## Trade-offs

- **Polling outbox vs CDC**: polling at 1 s adds ~1 s end-to-end latency but requires no additional infrastructure. Debezium CDC eliminates this at the cost of a connector cluster (see ADR-0003).
- **Spring Data JDBC vs JPA**: JDBC enforces explicit aggregate boundaries and prevents lazy-loading surprises; the trade-off is more verbose mapping for complex object graphs.
- **JSON envelope vs Avro**: the JSON envelope is zero-dependency; Avro + Schema Registry gives schema evolution guarantees at the cost of infrastructure (see ADR-0004).

## What I'd do next

- Add Avro schemas + Schema Registry for schema evolution guarantees.
- Add a `TransferProjection` read model for CQRS-style queries.
- Integrate GCP Secret Manager via Spring Cloud GCP.
- Add distributed tracing with OpenTelemetry (Micrometer Tracing is already on the classpath via Actuator).
- Harden the outbox relay with a circuit breaker (Resilience4j) so a Kafka outage doesn't block DB connections.
