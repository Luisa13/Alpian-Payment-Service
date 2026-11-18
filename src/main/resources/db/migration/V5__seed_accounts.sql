-- V5: Account table and seed data
-- Accounts are seeded here for local development and integration tests.
-- In production, accounts would be managed by a separate service.

CREATE TABLE accounts (
    id               UUID        NOT NULL,
    owner            VARCHAR(255) NOT NULL,
    balance_value    NUMERIC(19, 8) NOT NULL DEFAULT 0,
    balance_currency CHAR(3)     NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_accounts         PRIMARY KEY (id),
    CONSTRAINT chk_account_balance CHECK (balance_value >= 0)
);

-- Seed accounts for local dev and integration tests
INSERT INTO accounts (id, owner, balance_value, balance_currency) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Alice',   10000.00, 'CHF'),
    ('a0000000-0000-0000-0000-000000000002', 'Bob',      5000.00, 'CHF'),
    ('b0000000-0000-0000-0000-000000000001', 'Carol',    8000.00, 'CHF'),
    ('b0000000-0000-0000-0000-000000000002', 'Dave',     3000.00, 'CHF'),
    ('c0000000-0000-0000-0000-000000000001', 'Eve',      6000.00, 'CHF'),
    ('c0000000-0000-0000-0000-000000000002', 'Frank',    2000.00, 'CHF'),
    ('d0000000-0000-0000-0000-000000000001', 'Grace',   15000.00, 'CHF'),
    ('d0000000-0000-0000-0000-000000000002', 'Heidi',    4500.00, 'CHF');
