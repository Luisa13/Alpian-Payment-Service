-- V3: Add claimed_at to outbox_events to support the claim-then-send pattern.
-- The relay sets claimed_at in TX1 (short lock), sends to Kafka outside any transaction,
-- then marks published_at in TX2. Rows claimed but not published within claim-ttl-ms are
-- automatically reclaimed by the next relay cycle.
ALTER TABLE outbox_events ADD COLUMN claimed_at TIMESTAMPTZ;
