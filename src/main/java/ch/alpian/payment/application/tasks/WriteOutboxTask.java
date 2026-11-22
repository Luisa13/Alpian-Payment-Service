package ch.alpian.payment.application.tasks;

import ch.alpian.payment.application.events.PaymentEvents;
import ch.alpian.payment.application.events.PaymentPayload;
import ch.alpian.payment.domain.EventEnvelope;
import ch.alpian.payment.domain.OutboxRepository;
import ch.alpian.payment.domain.Payment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Task: append the event announcing the payment's current status to the transactional outbox.
 *
 * <p>Must run inside the same transaction as the state change so the outbox row is written
 * atomically with it. The outbox row id is the event id the consumer dedupes on.
 */
@Component
public class WriteOutboxTask implements Task<Payment, Void> {

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public WriteOutboxTask(OutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Override
    public Void execute(Payment payment) {
        String eventType = PaymentEvents.typeFor(payment.getStatus());
        EventEnvelope<PaymentPayload> envelope =
                EventEnvelope.of(eventType, PaymentEvents.EVENT_VERSION, correlationId(), PaymentPayload.from(payment));
        outbox.append(envelope.eventId(), payment.getId(), eventType, serialize(envelope));
        return null;
    }

    private static UUID correlationId() {
        String mdcValue = MDC.get("correlationId");
        if (mdcValue != null) {
            try {
                return UUID.fromString(mdcValue);
            } catch (IllegalArgumentException ignored) {
                // a client-supplied non-UUID correlation id is simply not propagated
            }
        }
        return null;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise event", e);
        }
    }
}
