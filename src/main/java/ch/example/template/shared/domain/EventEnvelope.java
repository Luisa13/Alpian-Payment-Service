package ch.example.template.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Versioned event envelope for all domain events published to Kafka.
 *
 * <p>Every event is wrapped in this envelope before serialisation. Consumers can route on {@code
 * type} and reject on unknown {@code version} before deserialising the payload.
 *
 * <p>Future options (noted in ADR-0004):
 *
 * <ul>
 *   <li>Schema Registry + Avro: replace this envelope with a generated Avro record and register
 *       the schema centrally.
 *   <li>CloudEvents spec: adopt the CNCF CloudEvents envelope format for interoperability.
 * </ul>
 *
 * @param <T> the payload type (should be a serialisable POJO or record)
 */
public record EventEnvelope<T>(
        UUID eventId, String type, int version, Instant occurredAt, UUID correlationId, T payload) {

    public EventEnvelope {
        if (eventId == null) throw new IllegalArgumentException("eventId must not be null");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type must not be blank");
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
        if (payload == null) throw new IllegalArgumentException("payload must not be null");
        // correlationId is nullable — may be absent for internally generated events
    }

    /** Create an envelope with a fresh event ID and the current time. */
    public static <T> EventEnvelope<T> of(String type, int version, UUID correlationId, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), type, version, Instant.now(), correlationId, payload);
    }
}
