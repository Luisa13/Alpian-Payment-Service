package ch.example.template.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.example.template.application.PaymentService;
import ch.example.template.domain.EventEnvelope;
import ch.example.template.domain.Money;
import ch.example.template.domain.Payment;
import ch.example.template.domain.PaymentRepository;
import ch.example.template.domain.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.utils.ContainerTestUtils;

class ConsumerIdempotencyIT extends IntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void duplicate_event_id_is_processed_only_once() throws Exception {
        Payment payment = Payment.create(
                new Money(new BigDecimal("10.00"), "CHF"), UUID.randomUUID(), UUID.randomUUID(), "dedup test");
        paymentRepository.save(payment);
        UUID paymentId = payment.getId();

        UUID sharedEventId = UUID.randomUUID();
        var payload = new PaymentService.PaymentPayload(
                paymentId, "10.00", "CHF", payment.getSourceAccountId(), payment.getTargetAccountId(), "dedup test");
        var envelope = new EventEnvelope<>(
                sharedEventId,
                PaymentService.EVENT_TYPE_REQUESTED,
                PaymentService.EVENT_VERSION,
                java.time.Instant.now(),
                UUID.randomUUID(),
                payload);
        String json = objectMapper.writeValueAsString(envelope);

        var settledRecords = Collections.synchronizedList(new java.util.ArrayList<ConsumerRecord<String, String>>());
        var containerProps = new ContainerProperties("payments.settled");
        containerProps.setGroupId("test-dedup-" + UUID.randomUUID());
        containerProps.setMessageListener((MessageListener<String, String>) settledRecords::add);
        var container = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        container.start();
        ContainerTestUtils.waitForAssignment(container, 1);

        try {
            kafkaTemplate.send(new ProducerRecord<>("payments.requested", paymentId.toString(), json));
            kafkaTemplate.send(new ProducerRecord<>("payments.requested", paymentId.toString(), json));
            kafkaTemplate.flush();

            String paymentKey = paymentId.toString();
            await().atMost(Duration.ofSeconds(15))
                    .until(() -> settledRecords.stream()
                            .anyMatch(r -> r.value() != null
                                    && r.value().contains("payment.settled")
                                    && paymentKey.equals(r.key())));

            Thread.sleep(500);

            long settledCount = settledRecords.stream()
                    .filter(r ->
                            r.value() != null && r.value().contains("payment.settled") && paymentKey.equals(r.key()))
                    .count();
            assertThat(settledCount).isEqualTo(1);

            Payment settled = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(settled.getPaymentStatus()).isInstanceOf(PaymentStatus.Settled.class);
        } finally {
            container.stop();
        }
    }
}
