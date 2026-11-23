# Payment Service API

Base URL (local development): `http://localhost:8080`  
OpenAPI specification: [`src/main/resources/openapi/api.yaml`](../src/main/resources/openapi/api.yaml)

All errors follow RFC 9457 `ProblemDetail` (`application/problem+json`) with a stable machine-readable `code` property (`VALIDATION_ERROR`, `MALFORMED_REQUEST`, `MISSING_HEADER`, `PAYMENT_NOT_FOUND`, `ACCOUNT_NOT_FOUND`, `UNKNOWN_ACCOUNT`, `CURRENCY_MISMATCH`, `INSUFFICIENT_FUNDS`, `IDEMPOTENCY_KEY_REUSED`, `CONCURRENT_MODIFICATION`, `INTERNAL_ERROR`).

## Table of Contents

- [Payments](#payments)
- [Accounts](#accounts)

---

## Payments

<details markdown="1">
<summary><code>POST /api/v1/payments</code> — Create and settle a payment for a sender account.</summary>

### Parameters

| Name | In | Type | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `Idempotency-Key` | header | `string` (1–64 chars) | required | Client-generated key that makes the request safe to retry. Replaying the same key and body returns `200`; reusing the key with a different body returns `422`. |
| `X-Request-Id` | header | `string` | optional | Correlation ID echoed back in the response header (a UUID is generated if omitted). |
| `amount` | body | `string` | required | Decimal string matching `^\d+(\.\d{1,2})?$`, greater than zero, at most 2 decimal places (e.g. `"100.00"`). |
| `currency` | body | `string` | required | ISO 4217 3-letter currency code matching `^[A-Z]{3}$` (e.g. `"CHF"`). |
| `senderAccountId` | body | `string (uuid)` | required | UUID of the sender account. |
| `receiverAccountId` | body | `string (uuid)` | optional* | UUID of an internal receiver account. Must differ from `senderAccountId`. |
| `receiverIban` | body | `string` | optional* | External receiver IBAN matching `^[A-Za-z]{2}\d{2}[A-Za-z0-9 ]{11,30}$` (e.g. `"CH9300762011623852957"`). |

*\*Exactly one of `receiverAccountId` or `receiverIban` must be provided, never both.*

### Responses

| HTTP Code | Content-Type | Response |
| :--- | :--- | :--- |
| `200` | `application/json` | Idempotent replay; returns the stored `PaymentResponse` with its current status. |
| `201` | `application/json` | Payment settled inside the request (`COMPLETED` or `FAILED`); returns `PaymentResponse`. |
| `202` | `application/json` | Funds held (`RESERVED`), settlement not confirmed yet. Includes `Location: /api/v1/payments/{id}` header and `PaymentResponse`. |
| `400` | `application/problem+json` | Validation error (`VALIDATION_ERROR` with `violations`), malformed JSON (`MALFORMED_REQUEST`), or missing header (`MISSING_HEADER`). |
| `409` | `application/problem+json` | Concurrent modification (`CONCURRENT_MODIFICATION`) after deadlock/lock-timeout retries were exhausted. |
| `422` | `application/problem+json` | Rejected at intake (`INSUFFICIENT_FUNDS`, `RAIL_REJECTED` with `paymentId`), `UNKNOWN_ACCOUNT`, `CURRENCY_MISMATCH`, or `IDEMPOTENCY_KEY_REUSED`. |
| `500` | `application/problem+json` | Unexpected error (`INTERNAL_ERROR` with `errorReference` for log correlation). |

Example `201 Created` response:

```json
{
  "id": "b1f5b8e0-7c3d-4a1e-9f2a-1234567890ab",
  "amount": "100.00",
  "currency": "CHF",
  "status": "COMPLETED",
  "failureReason": null,
  "senderAccountId": "a0000000-0000-0000-0000-000000000001",
  "receiverAccountId": "a0000000-0000-0000-0000-000000000002",
  "receiverIban": null,
  "railReference": "INT-b1f5b8e0",
  "createdAt": "2026-01-01T12:00:00Z",
  "updatedAt": "2026-01-01T12:00:00Z"
}
```

### Example cURL

Internal transfer:

```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-Request-Id: req-create-payment-1" \
  -d '{
    "amount": "100.00",
    "currency": "CHF",
    "senderAccountId": "a0000000-0000-0000-0000-000000000001",
    "receiverAccountId": "a0000000-0000-0000-0000-000000000002"
  }'
```

External IBAN transfer:

```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440001" \
  -H "X-Request-Id: req-create-payment-2" \
  -d '{
    "amount": "100.00",
    "currency": "CHF",
    "senderAccountId": "a0000000-0000-0000-0000-000000000001",
    "receiverIban": "CH9300762011623852957"
  }'
```

</details>

<details markdown="1">
<summary><code>GET /api/v1/payments/{id}</code> — Retrieve a payment by its ID.</summary>

### Parameters

| Name | In | Type | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | path | `string (uuid)` | required | UUID of the payment to retrieve. |
| `X-Request-Id` | header | `string` | optional | Correlation ID echoed back in the response header. |

### Responses

| HTTP Code | Content-Type | Response |
| :--- | :--- | :--- |
| `200` | `application/json` | Returns `PaymentResponse` (`id`, `amount`, `currency`, `status`, `failureReason`, `senderAccountId`, `receiverAccountId`, `receiverIban`, `railReference`, `createdAt`, `updatedAt`). |
| `400` | `application/problem+json` | Invalid `id` path parameter (`VALIDATION_ERROR`). |
| `404` | `application/problem+json` | Payment not found (`PAYMENT_NOT_FOUND`). |
| `500` | `application/problem+json` | Unexpected error (`INTERNAL_ERROR`). |

Example `200 OK` response:

```json
{
  "id": "b1f5b8e0-7c3d-4a1e-9f2a-1234567890ab",
  "amount": "100.00",
  "currency": "CHF",
  "status": "COMPLETED",
  "failureReason": null,
  "senderAccountId": "a0000000-0000-0000-0000-000000000001",
  "receiverAccountId": "a0000000-0000-0000-0000-000000000002",
  "receiverIban": null,
  "railReference": "INT-b1f5b8e0",
  "createdAt": "2026-01-01T12:00:00Z",
  "updatedAt": "2026-01-01T12:00:00Z"
}
```

### Example cURL

```bash
curl -s http://localhost:8080/api/v1/payments/b1f5b8e0-7c3d-4a1e-9f2a-1234567890ab \
  -H "X-Request-Id: req-get-payment-1"
```

</details>

---

## Accounts

<details markdown="1">
<summary><code>GET /api/v1/accounts/{id}</code> — Retrieve an account and its current and available balances.</summary>

### Parameters

| Name | In | Type | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | path | `string (uuid)` | required | UUID of the account to retrieve. |
| `X-Request-Id` | header | `string` | optional | Correlation ID echoed back in the response header. |

### Responses

| HTTP Code | Content-Type | Response |
| :--- | :--- | :--- |
| `200` | `application/json` | Returns `AccountResponse` (`id`, `ownerName`, `accountType` [`CUSTOMER` \| `CLEARING`], `currency`, `balance`, `availableBalance`). |
| `400` | `application/problem+json` | Invalid `id` path parameter (`VALIDATION_ERROR`). |
| `404` | `application/problem+json` | Account not found (`ACCOUNT_NOT_FOUND`). |
| `500` | `application/problem+json` | Unexpected error (`INTERNAL_ERROR`). |

Example `200 OK` response:

```json
{
  "id": "a0000000-0000-0000-0000-000000000001",
  "ownerName": "Alice",
  "accountType": "CUSTOMER",
  "currency": "CHF",
  "balance": "10000.00",
  "availableBalance": "9900.00"
}
```

### Example cURL

```bash
curl -s http://localhost:8080/api/v1/accounts/a0000000-0000-0000-0000-000000000001 \
  -H "X-Request-Id: req-get-account-1"
```

</details>
