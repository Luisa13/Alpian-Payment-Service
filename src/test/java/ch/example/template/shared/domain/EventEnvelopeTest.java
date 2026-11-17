package ch.example.template.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void creates_envelope_with_all_fields() {
        var correlationId = UUID.randomUUID();
        var envelope = EventEnvelope.of("transfer.requested", 1, correlationId, "payload");

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.type()).isEqualTo("transfer.requested");
        assertThat(envelope.version()).isEqualTo(1);
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.correlationId()).isEqualTo(correlationId);
        assertThat(envelope.payload()).isEqualTo("payload");
    }

    @Test
    void rejects_null_type() {
        assertThatThrownBy(() -> EventEnvelope.of(null, 1, UUID.randomUUID(), "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void rejects_blank_type() {
        assertThatThrownBy(() -> EventEnvelope.of("  ", 1, UUID.randomUUID(), "p"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_non_positive_version() {
        assertThatThrownBy(() -> EventEnvelope.of("type", 0, UUID.randomUUID(), "p"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allows_null_correlation_id() {
        // correlationId is optional — internally generated events may omit it
        var envelope = EventEnvelope.of("type", 1, null, "payload");
        assertThat(envelope.correlationId()).isNull();
    }

    @Test
    void rejects_null_event_id_via_direct_constructor() {
        assertThatThrownBy(() -> new EventEnvelope<>(null, "type", 1, java.time.Instant.now(), null, "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void rejects_null_occurred_at_via_direct_constructor() {
        assertThatThrownBy(() -> new EventEnvelope<>(UUID.randomUUID(), "type", 1, null, null, "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("occurredAt");
    }

    @Test
    void rejects_null_payload_via_direct_constructor() {
        assertThatThrownBy(() -> new EventEnvelope<>(UUID.randomUUID(), "type", 1, java.time.Instant.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void each_envelope_gets_unique_event_id() {
        var correlationId = UUID.randomUUID();
        var e1 = EventEnvelope.of("type", 1, correlationId, "p");
        var e2 = EventEnvelope.of("type", 1, correlationId, "p");
        assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }
}
