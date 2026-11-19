-- V2: Seed accounts for local development and integration tests.
-- Every non-zero opening balance has a matching opening ledger entry (payment_id NULL),
-- so SUM(ledger_entries.amount) per account always equals accounts.balance.
-- In production, accounts would be managed by a separate service.

INSERT INTO accounts (id, owner_name, account_type, currency, balance, available_balance) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Alice', 'CUSTOMER', 'CHF', 10000.0000, 10000.0000),
    ('a0000000-0000-0000-0000-000000000002', 'Bob',   'CUSTOMER', 'CHF',  5000.0000,  5000.0000),
    ('b0000000-0000-0000-0000-000000000001', 'Carol', 'CUSTOMER', 'CHF',  8000.0000,  8000.0000),
    ('b0000000-0000-0000-0000-000000000002', 'Dave',  'CUSTOMER', 'CHF',  3000.0000,  3000.0000),
    ('c0000000-0000-0000-0000-000000000001', 'Eve',   'CUSTOMER', 'CHF',  6000.0000,  6000.0000),
    ('c0000000-0000-0000-0000-000000000002', 'Frank', 'CUSTOMER', 'CHF',  2000.0000,  2000.0000),
    ('d0000000-0000-0000-0000-000000000001', 'Grace', 'CUSTOMER', 'CHF', 15000.0000, 15000.0000),
    ('d0000000-0000-0000-0000-000000000002', 'Heidi', 'CUSTOMER', 'CHF',  4500.0000,  4500.0000),
    ('f0000000-0000-0000-0000-000000000001', 'External clearing CHF', 'CLEARING', 'CHF', 0.0000, 0.0000);

-- Opening ledger entries (clearing accounts start at zero and have none).
INSERT INTO ledger_entries (id, payment_id, account_id, amount)
SELECT gen_random_uuid(), NULL, id, balance
FROM accounts
WHERE balance <> 0;
