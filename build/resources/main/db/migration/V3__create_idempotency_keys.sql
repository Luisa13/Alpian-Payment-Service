-- V3: HTTP idempotency table
-- Stores the first response for each (Idempotency-Key, request hash) pair.
-- The UNIQUE constraint on idempotency_key enforces at-database-level deduplication
-- — concurrent inserts for the same key race to a constraint violation, and the
-- loser retries by reading the winner's stored response.

CREATE TABLE idempotency_keys (
    idempotency_key   VARCHAR(255) NOT NULL,
    request_hash      CHAR(64)     NOT NULL,  -- SHA-256 hex of the serialised request body
    response_status   SMALLINT     NOT NULL,
    response_body     TEXT         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_idempotency_keys    PRIMARY KEY (idempotency_key)
);
