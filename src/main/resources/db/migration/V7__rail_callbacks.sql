-- V7: Audit trail and dedup table for external rail callbacks.
CREATE TABLE rail_callbacks (
    event_id    UUID        PRIMARY KEY,
    payment_id  UUID        NOT NULL REFERENCES payments (id),
    outcome     VARCHAR(16) NOT NULL CHECK (outcome IN ('SETTLED', 'REJECTED')),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_rail_callbacks_payment ON rail_callbacks (payment_id);
