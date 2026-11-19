package ch.alpian.payment.notification;

import ch.alpian.payment.application.events.PaymentEvents;
import ch.alpian.payment.application.events.PaymentPayload;
import ch.alpian.payment.domain.EventEnvelope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifies the sender of a payment about every state change.
 *
 * <p>Runs in its own consumer group. In one transaction it records the event in {@code
 * sent_notifications} (a duplicate means skip) and then logs the message. A failure rolls the
 * transaction back; the error handler retries with backoff and finally publishes the record to
 * {@code payment-notifications.DLT}.
 */
@Component
class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final SentNotificationStore sentNotifications;
    private final ObjectMapper objectMapper;
    private final Counter sentCounter;
    private final Counter duplicateCounter;
    private final Counter dltCounter;

    NotificationConsumer(SentNotificationStore sentNotifications, ObjectMapper objectMapper, MeterRegistry meters) {
        this.sentNotifications = sentNotifications;
        this.objectMapper = objectMapper;
        this.sentCounter = Counter.builder("payments.notifications.sent")
                .description("Notifications sent to payment senders")
                .register(meters);
        this.duplicateCounter = Counter.builder("payments.notifications.duplicates")
                .description("Redelivered events skipped because their notification was already sent")
                .register(meters);
        this.dltCounter = Counter.builder("payments.dlt.messages")
                .description("Messages that landed in the dead-letter topic")
                .register(meters);
    }

    @KafkaListener(topics = PaymentEvents.TOPIC, groupId = "${app.notification.group-id:payment-notifications}")
    @Transactional
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        EventEnvelope<PaymentPayload> event = deserialize(record.value());
        PaymentPayload payload = event.payload();
        String message = NotificationMessages.describe(event.type(), payload);

        boolean first = sentNotifications.tryRecord(
                event.eventId(), payload.paymentId(), payload.senderAccountId(), event.type(), message);
        if (!first) {
            duplicateCounter.increment();
            log.debug("Duplicate event skipped: eventId={}", event.eventId());
            return;
        }

        sentCounter.increment();
        log.info("NOTIFICATION to account {}: {}", payload.senderAccountId(), message);
    }

    @KafkaListener(
            topics = PaymentEvents.DLT_TOPIC,
            groupId = "${app.notification.group-id:payment-notifications}-dlt")
    public void onDeadLetter(ConsumerRecord<String, String> record) {
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
}
