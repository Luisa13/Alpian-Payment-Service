-- V2: Transactional outbox table
-- Outbox rows are written in the same transaction as the business row.
-- A scheduler polls for unsent rows using SELECT … FOR UPDATE SKIP LOCKED
-- and publishes them to Kafka.
--
-- Alternative: Debezium CDC (see ADR-0003) — streams outbox inserts directly
-- from the PostgreSQL WAL, eliminating the polling loop.

CREATE TABLE outbox_events (
    id              UUID        NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    message_key     VARCHAR(255) NOT NULL,
    payload         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent            BOOLEAN     NOT NULL DEFAULT false,
    sent_at         TIMESTAMPTZ,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

-- Partial index — only index unsent rows; sent rows don't need fast lookup.
CREATE INDEX idx_outbox_unsent ON outbox_events (created_at ASC)
    WHERE sent = false;
