# Testing

The default Gradle `test` task is disabled in `build.gradle.kts`. All automated tests are organized into dedicated test source sets and tasks, plus a shell-based smoke test script for live environments.

---

## Embedded in CD

These test suites run automatically during `./gradlew build` (and in the GitHub Actions CI pipeline `.github/workflows/ci.yml`):

- **`unitTest`** (`src/test/java/unitTest`): Fast, isolated unit tests with no Spring context and no external infrastructure. Covers domain entities/value objects, application flows and tasks, notification formatting, and architectural boundary rules enforced by ArchUnit (`ArchitectureTest`).
- **`integrationTest`** (`src/test/java/integrationTest`): Spring Boot integration tests backed by real PostgreSQL and Kafka containers via Testcontainers. Covers REST controllers, Flyway seed data, idempotency, concurrency/locking, settlement rails, `OutboxRelay`, `NotificationConsumer` (including DLT routing), and application health (`ApplicationSmokeIT`).
- **`e2eTest`** (`src/test/java/e2eTest`): End-to-end test suite configured with Testcontainers for full-flow verification.

In addition to running all three suites, `./gradlew build` enforces:
- **Spotless** (`spotlessCheck`): Code formatting check using `palantir-java-format`.
- **JaCoCo** (`jacocoTestCoverageVerification`): Requires at least **80% line and branch coverage** per class in `ch.alpian.payment.domain.*` and `ch.alpian.payment.application.*` across the aggregated execution data.

---

## Smoke Tests

Shell-based smoke tests run against a locally running stack (`make run`) via `Makefile` targets:

| Target | Script | Description | Customizable Parameters |
| :--- | :--- | :--- | :--- |
| `make smoke` | [`scripts/smoke.sh`](../scripts/smoke.sh) | Verifies `POST /api/v1/payments` (`201 Created`), idempotent replay (`200 OK`), key reuse with mismatched body (`422`), and Prometheus metrics (`payments_notifications_sent_total`, `outbox_backlog_events`). | `BASE_URL` (default `http://localhost:8080`) |
| `make smoke-outbox` | [`scripts/smoke-outbox.sh`](../scripts/smoke-outbox.sh) | Verifies the outbox relay happy path: creates a payment, waits for `PAYMENT_RESERVED` and `PAYMENT_COMPLETED` rows in `outbox_events` to be published, and checks that they land in Kafka `payment-notifications`. | `BASE_URL` (default `http://localhost:8080`), `COMPOSE` (default `docker compose`) |
| `make smoke-outbox-dlq` | [`scripts/smoke-outbox-dlq.sh`](../scripts/smoke-outbox-dlq.sh) | Temporarily stops Kafka, injects a poison `outbox_events` row, verifies it is dead-lettered after 5 attempts (`dead_lettered_at` set, `outbox.backlog.events > 0`), restarts Kafka, and confirms new payments are unblocked. | `BASE_URL` (default `http://localhost:8080`), `COMPOSE` (default `docker compose`) |
| `make smoke-burst` | [`scripts/smoke-burst.sh`](../scripts/smoke-burst.sh) | Fires concurrent `POST /api/v1/payments` requests, waits for all outbox events to be published, and verifies all payment IDs arrive in Kafka `payment-notifications`. | `BASE_URL` (default `http://localhost:8080`), `COMPOSE` (default `docker compose`), `BURST_SIZE` (default `10`) |
| `make smoke-resilience` | [`scripts/smoke-resilience.sh`](../scripts/smoke-resilience.sh) | Exercises liveness/readiness probes, error handling (`400`, `404`, `422`), external IBAN rail & `GET /api/v1/payments/{id}`, concurrent overdraft race & ledger invariants, simultaneous duplicate `Idempotency-Key` requests, and payment intake while Kafka is down. | `BASE_URL` (default `http://localhost:8080`), `COMPOSE` (default `docker compose`) |

### Examples

```bash
# Basic API & metrics smoke test against a custom port
BASE_URL=http://localhost:8081 make smoke

# Outbox happy-path and dead-letter tests
make smoke-outbox
make smoke-outbox-dlq

# Concurrent burst with 25 parallel payments
BURST_SIZE=25 make smoke-burst

# Resilience & concurrency suite
make smoke-resilience
```

---

## Gradle Commands

| Command | Description |
|---|---|
| `./gradlew unitTest` | Run unit tests only (no Docker required). |
| `./gradlew integrationTest` | Run integration tests (requires Docker for Testcontainers). |
| `./gradlew e2eTest` | Run end-to-end tests (requires Docker for Testcontainers). |
| `./gradlew unitTest integrationTest e2eTest` | Run all three test suites (`make test`, or `make test-parallel` for `--parallel`). |
| `./gradlew build` | Full verification: OpenAPI generation, compilation, all test suites, `spotlessCheck`, and `jacocoTestCoverageVerification`. |
| `./gradlew jacocoTestReport` | Generate aggregated HTML and XML coverage reports under `build/reports/jacoco/`. |

To run a specific test class or method:

```bash
./gradlew unitTest --tests "ch.alpian.payment.domain.PaymentTest"
./gradlew integrationTest --tests "ch.alpian.payment.integration.IdempotencyIT"
```
