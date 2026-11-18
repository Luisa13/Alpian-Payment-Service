package ch.example.template.domain;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId, String type, int version, Instant occurredAt, UUID correlationId, T payload) {

    public EventEnvelope {
        if (eventId == null) throw new IllegalArgumentException("eventId must not be null");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type must not be blank");
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
        if (payload == null) throw new IllegalArgumentException("payload must not be null");
    }

    public static <T> EventEnvelope<T> of(String type, int version, UUID correlationId, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), type, version, Instant.now(), correlationId, payload);
    }
}
