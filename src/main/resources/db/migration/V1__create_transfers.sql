-- V1: Transfer aggregate table
-- Optimistic locking via version column.
-- Money value object is stored as two flat columns.
-- Status stored as discriminator string + optional failure reason.

CREATE TABLE transfers (
    id               UUID        NOT NULL,
    amount_value     NUMERIC(19, 8) NOT NULL,
    amount_currency  CHAR(3)     NOT NULL,
    status_code      VARCHAR(20) NOT NULL,
    failure_reason   TEXT,
    source_account_id UUID       NOT NULL,
    target_account_id UUID       NOT NULL,
    description      VARCHAR(255),
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_transfers            PRIMARY KEY (id),
    CONSTRAINT chk_transfer_status    CHECK (status_code IN ('REQUESTED', 'SETTLED', 'FAILED')),
    CONSTRAINT chk_transfer_amount    CHECK (amount_value >= 0)
);

CREATE INDEX idx_transfers_status   ON transfers (status_code);
CREATE INDEX idx_transfers_created  ON transfers (created_at DESC);
