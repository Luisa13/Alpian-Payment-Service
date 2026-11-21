-- V4: Add dead_lettered_at to outbox_events to side-line events that exceed the max publish attempt cap.
-- The relay skips rows where dead_lettered_at IS NOT NULL, preventing a single bad event from
-- blocking all subsequent events indefinitely.
ALTER TABLE outbox_events ADD COLUMN dead_lettered_at TIMESTAMPTZ;
