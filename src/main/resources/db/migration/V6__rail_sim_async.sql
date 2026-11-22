-- V6: Make rail_sim_transfers asynchronous: outcome and reference are NULL until the dispatcher delivers.
ALTER TABLE rail_sim_transfers DROP CONSTRAINT rail_sim_transfers_outcome_check;
ALTER TABLE rail_sim_transfers ALTER COLUMN outcome DROP NOT NULL;
ALTER TABLE rail_sim_transfers ALTER COLUMN reference DROP NOT NULL;
ALTER TABLE rail_sim_transfers ADD CONSTRAINT rail_sim_transfers_outcome_check
    CHECK (outcome IS NULL OR outcome IN ('SETTLED', 'REJECTED'));

ALTER TABLE rail_sim_transfers ADD COLUMN deliver_at TIMESTAMPTZ;
ALTER TABLE rail_sim_transfers ADD COLUMN delivered_at TIMESTAMPTZ;

CREATE INDEX ix_rail_sim_pending ON rail_sim_transfers (deliver_at) WHERE delivered_at IS NULL;
