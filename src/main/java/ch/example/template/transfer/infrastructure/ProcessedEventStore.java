package ch.example.template.transfer.infrastructure;

import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Idempotency guard for Kafka consumers.
 *
 * <p>Before processing a message, call {@link #tryMarkProcessed(UUID, String)}. If it returns
 * {@code true}, proceed with processing. If it returns {@code false}, the event was already
 * handled — skip it silently.
 *
 * <p>The uniqueness constraint on {@code processed_events.event_id} guarantees correctness even
 * under concurrent redelivery.
 */
@Component
class ProcessedEventStore {

    private final JdbcClient jdbc;

    ProcessedEventStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Atomically marks the event as processed.
     *
     * @return {@code true} if this is the first time we see this event; {@code false} if already
     *     processed (duplicate)
     */
    boolean tryMarkProcessed(UUID eventId, String topic) {
        try {
            jdbc.sql("""
                    INSERT INTO processed_events (event_id, topic)
                    VALUES (:eventId, :topic)
                    """).param("eventId", eventId).param("topic", topic).update();
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
