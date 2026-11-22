-- V5: Add CHF clearing account shards to avoid single-row lock contention
-- during concurrent external payment finalizations.
INSERT INTO accounts (id, owner_name, account_type, currency, balance, available_balance) VALUES
    ('f0000000-0000-0000-0000-000000000002', 'External clearing CHF shard 2', 'CLEARING', 'CHF', 0.0000, 0.0000),
    ('f0000000-0000-0000-0000-000000000003', 'External clearing CHF shard 3', 'CLEARING', 'CHF', 0.0000, 0.0000),
    ('f0000000-0000-0000-0000-000000000004', 'External clearing CHF shard 4', 'CLEARING', 'CHF', 0.0000, 0.0000),
    ('f0000000-0000-0000-0000-000000000005', 'External clearing CHF shard 5', 'CLEARING', 'CHF', 0.0000, 0.0000),
    ('f0000000-0000-0000-0000-000000000006', 'External clearing CHF shard 6', 'CLEARING', 'CHF', 0.0000, 0.0000),
    ('f0000000-0000-0000-0000-000000000007', 'External clearing CHF shard 7', 'CLEARING', 'CHF', 0.0000, 0.0000),
    ('f0000000-0000-0000-0000-000000000008', 'External clearing CHF shard 8', 'CLEARING', 'CHF', 0.0000, 0.0000);
