-- V5: Add SUBMITTED payment state for async rail submission.
ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('REJECTED', 'RESERVED', 'SUBMITTED', 'COMPLETED', 'FAILED'));

ALTER TABLE payments ADD COLUMN submitted_at TIMESTAMPTZ;
CREATE INDEX ix_payments_submitted ON payments (submitted_at) WHERE status = 'SUBMITTED';
