-- V1: Phase 1 schema (reserve -> settle -> finalize), double-entry ledger, outbox, notifications.
-- Money is NUMERIC(19,4) everywhere. Seed data lives in V2.

CREATE TABLE accounts (
    id                UUID PRIMARY KEY,
    owner_name        VARCHAR(100)  NOT NULL,
    account_type      VARCHAR(16)   NOT NULL DEFAULT 'CUSTOMER'
        CHECK (account_type IN ('CUSTOMER', 'CLEARING')),  -- CLEARING = counterpart for external payments
    currency          CHAR(3)       NOT NULL,
    balance           NUMERIC(19,4) NOT NULL CHECK (balance >= 0),
    available_balance NUMERIC(19,4) NOT NULL CHECK (available_balance >= 0),
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT available_le_balance CHECK (available_balance <= balance)
);

CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    idempotency_key     VARCHAR(64)   NOT NULL,
    request_hash        CHAR(64)      NOT NULL,                  -- SHA-256 hex of the normalized body
    sender_account_id   UUID          NOT NULL REFERENCES accounts (id),
    receiver_type       VARCHAR(8)    NOT NULL CHECK (receiver_type IN ('INTERNAL', 'EXTERNAL')),
    receiver_account_id UUID          REFERENCES accounts (id),
    receiver_iban       VARCHAR(34),
    amount              NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency            CHAR(3)       NOT NULL,
    status              VARCHAR(16)   NOT NULL
        CHECK (status IN ('REJECTED', 'RESERVED', 'COMPLETED', 'FAILED')),
    failure_reason      VARCHAR(50),                             -- INSUFFICIENT_FUNDS, RAIL_REJECTED, ...
    rail_reference      VARCHAR(64),
    settlement_attempts INT           NOT NULL DEFAULT 0,
    version             BIGINT        NOT NULL DEFAULT 0,
    reserved_at         TIMESTAMPTZ,
    finalized_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT receiver_shape CHECK (
        (receiver_type = 'INTERNAL' AND receiver_account_id IS NOT NULL AND receiver_iban IS NULL) OR
        (receiver_type = 'EXTERNAL' AND receiver_iban IS NOT NULL AND receiver_account_id IS NULL)),
    CONSTRAINT no_self_payment CHECK (receiver_account_id IS DISTINCT FROM sender_account_id)
);
CREATE INDEX ix_payments_sender ON payments (sender_account_id);
CREATE INDEX ix_payments_reserved ON payments (reserved_at) WHERE status = 'RESERVED';  -- recovery scan

CREATE TABLE ledger_entries (
    id          UUID PRIMARY KEY,
    payment_id  UUID          REFERENCES payments (id),          -- NULL only for opening balances
    account_id  UUID          NOT NULL REFERENCES accounts (id),
    amount      NUMERIC(19,4) NOT NULL CHECK (amount <> 0),      -- signed: negative = debit
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_ledger_payment_account UNIQUE (payment_id, account_id)  -- a payment posts at most once per account
);
CREATE INDEX ix_ledger_account ON ledger_entries (account_id);

CREATE TABLE outbox_events (
    id               UUID PRIMARY KEY,                           -- also the eventId the consumer dedupes on
    aggregate_id     UUID         NOT NULL,                      -- payment id = Kafka message key
    event_type       VARCHAR(40)  NOT NULL,                      -- PAYMENT_RESERVED / _COMPLETED / _FAILED / _REJECTED
    payload          JSONB        NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at     TIMESTAMPTZ,
    publish_attempts INT          NOT NULL DEFAULT 0,
    last_error       VARCHAR(500)
);
CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE sent_notifications (
    event_id    UUID PRIMARY KEY,                                -- dedupe: a second delivery hits this key
    payment_id  UUID         NOT NULL,
    account_id  UUID         NOT NULL,                           -- the sender being notified
    event_type  VARCHAR(40)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    sent_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE rail_sim_transfers (
    payment_id  UUID PRIMARY KEY,                                -- the rail's idempotency key
    outcome     VARCHAR(16)  NOT NULL CHECK (outcome IN ('SETTLED', 'REJECTED')),
    reference   VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE shedlock (
    name        VARCHAR(64)  PRIMARY KEY,
    lock_until  TIMESTAMP    NOT NULL,
    locked_at   TIMESTAMP    NOT NULL,
    locked_by   VARCHAR(255) NOT NULL
);
