package ch.alpian.payment.infrastructure.persistence;

import ch.alpian.payment.domain.OutboxRepository;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Writes to the transactional outbox. Must be called inside the same transaction as the state
 * change it describes, so the event exists if and only if the change committed.
 */
@Repository
class OutboxStore implements OutboxRepository {

    private final JdbcClient jdbc;

    OutboxStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param eventId   also the id the notification consumer dedupes on
     * @param aggregateId the payment id, used as the Kafka message key
     * @param payloadJson the serialized event envelope
     */
    @Override
    public void append(UUID eventId, UUID aggregateId, String eventType, String payloadJson) {
        jdbc.sql("""
                        INSERT INTO outbox_events (id, aggregate_id, event_type, payload)
                        VALUES (:id, :aggregateId, :eventType, CAST(:payload AS jsonb))
                        """)
                .param("id", eventId)
                .param("aggregateId", aggregateId)
                .param("eventType", eventType)
                .param("payload", payloadJson)
                .update();
    }
}
