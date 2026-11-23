package ch.alpian.payment.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.alpian.payment.application.events.PaymentEvents;
import ch.alpian.payment.application.events.PaymentPayload;
import ch.alpian.payment.domain.EventEnvelope;
import ch.alpian.payment.integration.PaymentIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.TestPropertySource;

/** T11: notification consumer deduplication and dead-letter topic routing. */
@TestPropertySource(
        properties = {
            "app.notification.group-id=notification-consumer-it",
            "spring.kafka.consumer.auto-offset-reset=latest"
        })
class NotificationConsumerIT extends PaymentIntegrationTest {

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MeterRegistry meters;

    @Value("${app.kafka.topics.partitions}")
    int partitions;

    @BeforeEach
    void waitForConsumerReady() throws InterruptedException {
        var container = registry.getAllListenerContainers().stream()
                .filter(c -> "notification-consumer-it".equals(c.getGroupId()))
                .findFirst()
                .orElseThrow();
        ContainerTestUtils.waitForAssignment(container, partitions);
    }

    @Test
    void listener_container_uses_record_ack_mode() {
        var container = registry.getAllListenerContainers().stream()
                .filter(c -> "notification-consumer-it".equals(c.getGroupId()))
                .findFirst()
                .orElseThrow();

        assertThat(container.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.RECORD);
    }

    @Test
    void same_event_delivered_twice_produces_one_notification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        var payload =
                new PaymentPayload(paymentId, senderId, UUID.randomUUID(), null, "42.00", "CHF", "COMPLETED", null);
        var envelope = new EventEnvelope<>(
                eventId,
                PaymentEvents.PAYMENT_COMPLETED,
                PaymentEvents.EVENT_VERSION,
                Instant.now(),
                UUID.randomUUID(),
                payload);
        String json = objectMapper.writeValueAsString(envelope);

        double duplicatesBefore =
                meters.counter("payments.notifications.duplicates").count();

        kafkaTemplate.send(PaymentEvents.TOPIC, paymentId.toString(), json).get();
        kafkaTemplate.send(PaymentEvents.TOPIC, paymentId.toString(), json).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(meters.counter("payments.notifications.duplicates").count())
                    .isGreaterThanOrEqualTo(duplicatesBefore + 1.0);
        });

        long rows = count("SELECT COUNT(*) FROM sent_notifications WHERE event_id = :id", eventId);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void poison_message_ends_up_in_the_dlt() throws Exception {
        double dltBefore = meters.counter("payments.dlt.messages").count();

        kafkaTemplate
                .send(PaymentEvents.TOPIC, UUID.randomUUID().toString(), "{not-valid-json")
                .get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(meters.counter("payments.dlt.messages").count()).isGreaterThanOrEqualTo(dltBefore + 1.0);
        });
    }
}
