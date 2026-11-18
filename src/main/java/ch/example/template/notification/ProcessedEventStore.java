package ch.example.template.notification;

import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Idempotency guard for Kafka consumers.
 *
 * <p>Before processing a message, call {@link #tryMarkProcessed(UUID, String)}. Returns {@code
 * true} on first processing, {@code false} for duplicates.
 */
@Component
class ProcessedEventStore {

    private final JdbcClient jdbc;

    ProcessedEventStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

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
