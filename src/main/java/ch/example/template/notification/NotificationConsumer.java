package ch.example.template.notification;

import ch.example.template.api.CorrelationIdFilter;
import ch.example.template.application.events.PaymentEvents;
import ch.example.template.application.events.PaymentPayload;
import ch.example.template.domain.EventEnvelope;
import ch.example.template.domain.Payment;
import ch.example.template.domain.PaymentRepository;
import ch.example.template.infrastructure.persistence.OutboxEntry;
import ch.example.template.infrastructure.persistence.OutboxRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for {@code payments.requested}.
 *
 * <p>Simulates settlement and emits a {@code payments.settled} event via the outbox.
 *
 * <p>Reliability pattern:
 *
 * <ul>
 *   <li><b>Consumer idempotency</b>: checks {@link ProcessedEventStore} before any state mutation.
 *   <li><b>Retry with backoff</b>: configured in {@link
 *       ch.example.template.infrastructure.messaging.KafkaConfig}.
 *   <li><b>DLT</b>: unrecoverable messages land in {@code payments.requested.DLT}.
 * </ul>
 */
@Component
class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outbox;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper objectMapper;

    private final Counter settledCounter;
    private final Counter failedCounter;
    private final Counter dltCounter;

    NotificationConsumer(
            PaymentRepository paymentRepository,
            OutboxRepository outbox,
            ProcessedEventStore processedEvents,
            ObjectMapper objectMapper,
            MeterRegistry meters) {
        this.paymentRepository = paymentRepository;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;

        this.settledCounter = Counter.builder("payments.settled")
                .description("Number of payments successfully settled")
                .register(meters);
        this.failedCounter = Counter.builder("payments.failed")
                .description("Number of payments that failed settlement")
                .tag("reason", "settlement_error")
                .register(meters);
        this.dltCounter = Counter.builder("payments.dlt.messages")
                .description("Number of messages sent to the dead-letter topic")
                .register(meters);
    }

    @KafkaListener(topics = PaymentEvents.TOPIC_REQUESTED, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onPaymentRequested(ConsumerRecord<String, String> record) {

        Header corrHeader = record.headers().lastHeader("X-Correlation-Id");
        if (corrHeader != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, new String(corrHeader.value()));
        }

        try {
            EventEnvelope<PaymentPayload> envelope = deserialize(record.value());

            if (!processedEvents.tryMarkProcessed(envelope.eventId(), record.topic())) {
                log.debug("Duplicate event skipped: eventId={}", envelope.eventId());
                return;
            }

            UUID paymentId = UUID.fromString(record.key());
            Payment payment = paymentRepository
                    .findById(paymentId)
                    .orElseThrow(() -> new IllegalStateException("Payment not found for event: " + paymentId));

            payment.settle();
            paymentRepository.save(payment);

            var settledEnvelope = EventEnvelope.of(
                    PaymentEvents.EVENT_TYPE_SETTLED,
                    PaymentEvents.EVENT_VERSION,
                    envelope.correlationId(),
                    PaymentPayload.from(payment));
            String payload = serialize(settledEnvelope);
            outbox.save(OutboxEntry.of(PaymentEvents.TOPIC_SETTLED, paymentId.toString(), payload));

            settledCounter.increment();
            log.info("Payment settled id={}", paymentId);

        } catch (Exception e) {
            failedCounter.increment();
            throw e;
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    @KafkaListener(topics = PaymentEvents.TOPIC_REQUESTED + "-dlt", groupId = "${spring.kafka.consumer.group-id}-dlt")
    public void onDlt(ConsumerRecord<String, String> record) {
        dltCounter.increment();
        log.error("Message landed in DLT: topic={} key={} offset={}", record.topic(), record.key(), record.offset());
    }

    private EventEnvelope<PaymentPayload> deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<EventEnvelope<PaymentPayload>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialise payment event: " + e.getMessage(), e);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialise event", e);
        }
    }
}
