package ch.alpian.payment.domain;

import java.util.UUID;

/**
 * Port: transactional outbox storage. Must be called inside the same transaction as the state
 * change it describes, so the event exists if and only if the change committed.
 */
public interface OutboxRepository {

    /**
     * @param eventId also the id the notification consumer dedupes on
     * @param aggregateId the payment id, used as the Kafka message key
     * @param payloadJson the serialized event envelope
     */
    void append(UUID eventId, UUID aggregateId, String eventType, String payloadJson);
}
