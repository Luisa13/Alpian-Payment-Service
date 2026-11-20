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
