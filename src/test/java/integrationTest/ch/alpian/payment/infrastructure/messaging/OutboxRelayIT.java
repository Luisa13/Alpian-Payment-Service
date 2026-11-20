package ch.alpian.payment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import ch.alpian.payment.api.generated.model.CreatePaymentRequest;
import ch.alpian.payment.api.generated.model.PaymentResponse;
import ch.alpian.payment.application.events.PaymentEvents;
import ch.alpian.payment.integration.PaymentIntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/** T10: outbox relay publishes one Kafka record per state change and drains backlog after outage. */
@TestPropertySource(properties = "app.outbox.relay-interval-ms=60000")
class OutboxRelayIT extends PaymentIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    PlatformTransactionManager txManager;

    @Autowired
    OutboxRelay outboxRelay;

    @Test
    void each_state_change_produces_one_kafka_record_keyed_by_payment_id() {
        UUID sender = newAccount("200.00");
        UUID receiver = newAccount("0.00");

        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of(PaymentEvents.TOPIC));
            consumer.poll(Duration.ofMillis(500));

            ResponseEntity<PaymentResponse> response = postPayment(sender, receiver, "25.00");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            UUID paymentId = response.getBody().getId();

            outboxRelay.relay();

            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(200));
                for (ConsumerRecord<String, String> r : polled) {
                    if (paymentId.toString().equals(r.key())) {
                        records.add(r);
                    }
                }
                assertThat(records).hasSize(2);
            });

            List<String> eventTypes = records.stream()
                    .map(r -> new String(r.headers().lastHeader("eventType").value()))
                    .toList();
            assertThat(eventTypes).containsExactly("PAYMENT_RESERVED", "PAYMENT_COMPLETED");
        }
    }

    @Test
    void when_kafka_fails_relay_records_attempt_and_backlog_drains_on_recovery() {
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        String envelope = """
                {
                  "eventId": "%s",
                  "eventType": "PAYMENT_COMPLETED",
                  "eventVersion": 1,
                  "occurredAt": "%s",
                  "aggregateId": "%s",
                  "payload": {
                    "paymentId": "%s",
                    "senderAccountId": "%s",
                    "amount": "10.00",
                    "currency": "CHF",
                    "status": "COMPLETED"
                  }
                }
                """.formatted(eventId, Instant.now(), paymentId, paymentId, senderId);

        KafkaTemplate<String, String> failingTemplate = spy(kafkaTemplate);
        doReturn(CompletableFuture.failedFuture(new RuntimeException("Simulated Kafka outage")))
                .when(failingTemplate)
                .send(any(ProducerRecord.class));
        var failingRelay = new OutboxRelay(jdbc, failingTemplate, new SimpleMeterRegistry(), txManager, 1000, 30000);

        jdbc.sql("""
                        INSERT INTO outbox_events (id, aggregate_id, event_type, payload)
                        VALUES (:id, :agg, 'PAYMENT_COMPLETED', CAST(:payload AS jsonb))
                        """)
                .param("id", eventId)
                .param("agg", paymentId)
                .param("payload", envelope)
                .update();

        try {
            failingRelay.relay();

            Integer attempts = jdbc.sql("SELECT publish_attempts FROM outbox_events WHERE id = :id")
                    .param("id", eventId)
                    .query(Integer.class)
                    .single();
            String lastError = jdbc.sql("SELECT last_error FROM outbox_events WHERE id = :id")
                    .param("id", eventId)
                    .query(String.class)
                    .single();
            assertThat(attempts).isEqualTo(1);
            assertThat(lastError).contains("Simulated Kafka outage");

            outboxRelay.relay();

            long unpublished = jdbc.sql("SELECT COUNT(*) FROM outbox_events WHERE id = :id AND published_at IS NULL")
                    .param("id", eventId)
                    .query(Long.class)
                    .single();
            assertThat(unpublished).isZero();
        } finally {
            jdbc.sql("DELETE FROM outbox_events WHERE id = :id")
                    .param("id", eventId)
                    .update();
        }
    }

    private ResponseEntity<PaymentResponse> postPayment(UUID sender, UUID receiver, String amount) {
        var req = new CreatePaymentRequest()
                .amount(amount)
                .currency("CHF")
                .senderAccountId(sender)
                .receiverAccountId(receiver);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "outbox-" + UUID.randomUUID());
        return http.postForEntity("/api/v1/payments", new HttpEntity<>(req, headers), PaymentResponse.class);
    }

    private KafkaConsumer<String, String> createConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "outbox-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "latest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class));
    }
}
