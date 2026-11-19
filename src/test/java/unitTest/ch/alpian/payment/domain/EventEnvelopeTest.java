package ch.alpian.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void of_creates_envelope_with_generated_id_and_now() {
        UUID correlationId = UUID.randomUUID();
        var envelope = EventEnvelope.of("payment.requested", 1, correlationId, "payload");

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.type()).isEqualTo("payment.requested");
        assertThat(envelope.version()).isEqualTo(1);
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.correlationId()).isEqualTo(correlationId);
        assertThat(envelope.payload()).isEqualTo("payload");
    }

    @Test
    void constructor_rejects_null_event_id() {
        assertThatThrownBy(() -> new EventEnvelope<>(null, "type", 1, Instant.now(), null, "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void constructor_rejects_blank_type() {
        assertThatThrownBy(() -> new EventEnvelope<>(UUID.randomUUID(), "", 1, Instant.now(), null, "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void constructor_rejects_version_less_than_1() {
        assertThatThrownBy(() -> new EventEnvelope<>(UUID.randomUUID(), "type", 0, Instant.now(), null, "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    void constructor_rejects_null_occurred_at() {
        assertThatThrownBy(() -> new EventEnvelope<>(UUID.randomUUID(), "type", 1, null, null, "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("occurredAt");
    }

    @Test
    void constructor_rejects_null_payload() {
        assertThatThrownBy(() -> new EventEnvelope<>(UUID.randomUUID(), "type", 1, Instant.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void correlation_id_may_be_null() {
        var envelope = new EventEnvelope<>(UUID.randomUUID(), "type", 1, Instant.now(), null, "p");
        assertThat(envelope.correlationId()).isNull();
    }
}
