# Running Locally

## Prerequisites

- **Java 21**
- **Docker Desktop** (or a compatible Docker daemon)
- **`make`**

---

## 1. Start the Infrastructure Containers

Start PostgreSQL, Kafka (KRaft), Kafka UI, Prometheus, and Grafana using `make`:

```bash
make up
```

This runs `docker compose up -d --wait` and initializes the `payment-notifications` and `payment-notifications.DLT` Kafka topics. Once ready, the following endpoints are available:

| Service | Address | Notes |
|---|---|---|
| PostgreSQL | `localhost:5432` | DB `paymentdb`, user `payment`, password `payment` |
| Kafka | `localhost:9092` | External listener |
| Kafka UI | http://localhost:8090 | Topic & consumer inspection |
| Prometheus | http://localhost:9090 | Scrapes `/actuator/prometheus` |
| Grafana | http://localhost:3000 | Credentials: `admin` / `admin` |

---

## 2. Start the Application

### Option A: Run from the host with Gradle (recommended for local dev)

```bash
./gradlew bootRun
```

`bootRun` automatically activates the `local` Spring profile (`-Dspring.profiles.active=local`), which connects to `localhost:5432` and `localhost:9092` and enables plain-text console logging.

### Option B: Run containerized via Make

If you uncomment the `app` service in `docker-compose.yml`, you can build the Jib image and start the full stack in Docker:

```bash
make run
```

---

## 3. Run a Sample Transaction

### Using `make`

Run the built-in smoke script (`scripts/smoke.sh`), which creates a payment, verifies idempotent replay, checks body-mismatch conflict handling (`422`), and inspects Prometheus metrics:

```bash
make smoke
```

### Using `curl`

Flyway seeds sample customer accounts in `V2__seed_accounts.sql` (including Alice `a0000000-0000-0000-0000-000000000001` with `10000.0000 CHF` and Bob `a0000000-0000-0000-0000-000000000002` with `5000.0000 CHF`).

Send an internal payment from Alice to Bob:

```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen | tr '[:upper:]' '[:lower:]')" \
  -d '{
    "amount": "42.00",
    "currency": "CHF",
    "senderAccountId": "a0000000-0000-0000-0000-000000000001",
    "receiverAccountId": "a0000000-0000-0000-0000-000000000002"
  }'
```

Expected response: `201 Created` with the `PaymentResponse` JSON containing the generated `id` and `status: "COMPLETED"`.

You can then query the payment or sender account:

```bash
curl -s http://localhost:8080/api/v1/accounts/a0000000-0000-0000-0000-000000000001
```

---

## 4. Stop the Containers

Stop and remove the containers and network (preserving the `postgres_data` volume):

```bash
make down
```
