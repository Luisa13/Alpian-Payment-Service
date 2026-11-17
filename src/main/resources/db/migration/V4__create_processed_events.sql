-- V4: Kafka consumer idempotency table
-- Records the event IDs of successfully processed Kafka messages.
-- Before processing a message, the consumer checks this table; if the event_id
-- is already present it skips processing. This makes the consumer idempotent
-- under redelivery (e.g. after a crash before offset commit).

CREATE TABLE processed_events (
    event_id    UUID        NOT NULL,
    topic       VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);
