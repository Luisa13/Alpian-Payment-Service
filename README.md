# Payment Service
---
Payment Service (Spring Boot, Java 21, Gradle, PostgreSQL, Kafka) where users create payments over REST. It must check the balance before processing, handle concurrency (no double spending, no double notifications), store transactions in PostgreSQL and notify the sender through Kafka.

---

## Prerequisites

- Java 21
- Docker Desktop (or compatible daemon)
- `make`

---

## Quickstart
How to run this project in a nutshell. For more information look at [running locally in the docs](docs/running-locally.md)

```bash
git clone <repo-url> payment-service
cd payment-service
make up        # starts Postgres, Kafka, Prometheus, Grafana and Docs
make smoke     # exercises the HTTP API and prints metrics
```

Service | Address
--------|----------
Grafana| http://localhost:3000 (admin / admin)
Kafka UI| http://localhost:8090
Prometheus| http://localhost:9090
Documentation|  http://localhost:8000/

---

## Architecture overview
You can find it in [Architecture](docs/architecture.md)

---

## Assumptions
You can find a more exhaustive analysis in the Assumptions & Scope section within [Design Assumptions & Philosophy](docs/design-assumptions-philosophy.md)

- **Gateway handles security**. I'm assuming an API gateway in front of the service authenticates callers, checks that they own the sender account, and applies TLS and rate limits.
- **Callers want an answer right away**. A client expects to learn the result in the same request, or get a 202 and check back later.
- **No real payment network**. Transfers between two of our accounts move money inside our own database. Payments to an external IBAN are settled by a simulated rail and an internal CLEARING account, not by SEPA, SWIFT or a card network.
- **One currency per payment**. The sender, the receiver and the request all use the same currency.
- **Simple balance rule**. A payment is accepted if the sender has enough available funds. Accounts can never go below zero, so there is no overdraft.
- **Payments are final**. Once a payment reaches a final state, it never changes (ADR-0005).
- **Notifying the sender means publishing an event**. I publish a Kafka event and record it in sent_notifications. No email or SMS provider is involved.
- **Advanced outbox delivery guarantees**: Cross-replica event ordering and CDC-based outbox delivery are outside the scope of this implementation.
- **Additional platform and payment capabilities**: Asynchronous external rail callbacks and webhooks (ADR-0020), multi-currency settlement and FX, API authentication and rate limiting, and archival of historical payments and outbox_events data are not covered.
- Secret rotation is out of scope

## Trade-offs

- **Polling outbox vs CDC**: polling at 1 s adds ~1 s end-to-end latency but requires no additional infrastructure. Debezium CDC eliminates this at the cost of a connector cluster (see ADR-0003).
- **Spring Data JDBC vs JPA**: JDBC enforces explicit aggregate boundaries and prevents lazy-loading surprises; the trade-off is more verbose mapping for complex object graphs.
