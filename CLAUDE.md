# CLAUDE.md

## Project Overview

Alpian Payment Service is a Java 21 / Spring Boot microservice (`ch.alpian.payment`) implementing payment intake, fund reservation, settlement across internal and simulated external rails, double-entry ledger persistence, transactional outbox publishing to Kafka, and idempotent notification consumption with dead-letter topic (DLT) routing.

## Development Conventions and Style

- **Formatting**: Java code under `src/**/*.java` is formatted with Spotless using `palantirJavaFormat` (trailing whitespace trimmed, newline at EOF).
- **Architecture**: Layered DDD package structure (`api`, `application`, `domain`, `infrastructure`, `notification`), with boundary rules enforced by ArchUnit in `unitTest`.
- **Contract-first API**: The OpenAPI specification at `src/main/resources/openapi/api.yaml` generates Spring interfaces and models into `build/generated/openapi` during the build.
- **Testing & Coverage**: The default Gradle `test` task is disabled; tests are separated into `unitTest` (no Spring/infrastructure), `integrationTest` (Spring context + Testcontainers), and `e2eTest` (Testcontainers). JaCoCo requires at least 80% line and branch coverage per class in `ch.alpian.payment.domain.*` and `ch.alpian.payment.application.*`.

## Important Commands

1. `./gradlew build` — Full verification: OpenAPI generation, compilation, `unitTest`, `integrationTest`, `e2eTest`, Spotless check, and JaCoCo coverage verification.
2. `./gradlew unitTest` — Run fast unit tests (including ArchUnit tests) without Testcontainers.
3. `./gradlew integrationTest` — Run integration tests with PostgreSQL and Kafka via Testcontainers.
4. `./gradlew spotlessApply` — Auto-format Java files to match the Spotless (`palantirJavaFormat`) configuration.
5. `./gradlew openApiGenerate` — Regenerate Spring API interfaces and models from `src/main/resources/openapi/api.yaml`.

## Known Issues

- `NotificationConsumerIT > same_event_delivered_twice_produces_one_notification()` can fail intermittently during full runs due to `spring.kafka.consumer.auto-offset-reset=latest` racing with consumer group startup, then pass on subsequent runs.
- **Do not add inline defaults to `@Value` / `@Scheduled` / `@KafkaListener` annotations** (e.g. `${app.foo:someDefault}`). All `app.*` defaults live in `application.yml`; inline defaults silently mask missing or mistyped keys instead of failing fast. Hardcoded values that belong in `application.yml` should be extracted there and, where a group of related settings is involved, bound via a `@ConfigurationProperties` record (see `SimulatedRailProperties` and `KafkaProperties` as examples).
