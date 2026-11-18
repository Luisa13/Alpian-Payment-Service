package ch.example.template.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.example.template.domain.PaymentRepository;
import ch.example.template.domain.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.utils.ContainerTestUtils;

class PaymentEndToEndIT extends IntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void post_payment_reaches_settled_state_end_to_end() throws Exception {
        var settledRecords = Collections.synchronizedList(new java.util.ArrayList<ConsumerRecord<String, String>>());
        var containerProps = new ContainerProperties("payments.settled");
        containerProps.setGroupId("test-e2e-" + UUID.randomUUID());
        containerProps.setMessageListener((MessageListener<String, String>) settledRecords::add);
        var container = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        container.start();
        ContainerTestUtils.waitForAssignment(container, 1);

        try {
            String idempotencyKey = UUID.randomUUID().toString();
            String body = """
                    {
                      "amount": "75.00",
                      "currency": "CHF",
                      "sourceAccountId": "d0000000-0000-0000-0000-000000000001",
                      "targetAccountId": "d0000000-0000-0000-0000-000000000002",
                      "description": "end-to-end test"
                    }
                    """;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotency-Key", idempotencyKey);

            var createResp = restTemplate.postForEntity("/v1/payments", new HttpEntity<>(body, headers), String.class);
            assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            JsonNode responseJson = objectMapper.readTree(createResp.getBody());
            UUID paymentId = UUID.fromString(responseJson.get("id").asText());

            await().atMost(Duration.ofSeconds(20))
                    .until(() -> settledRecords.stream()
                            .anyMatch(r -> r.value() != null
                                    && r.value().contains("payment.settled")
                                    && r.key().equals(paymentId.toString())));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var payment = paymentRepository.findById(paymentId).orElseThrow();
                assertThat(payment.getPaymentStatus()).isInstanceOf(PaymentStatus.Settled.class);
            });

            ConsumerRecord<String, String> settledEvent = settledRecords.stream()
                    .filter(r -> r.key().equals(paymentId.toString()))
                    .findFirst()
                    .orElseThrow();
            JsonNode eventJson = objectMapper.readTree(settledEvent.value());
            assertThat(eventJson.get("type").asText()).isEqualTo("payment.settled");
            assertThat(eventJson.get("version").asInt()).isEqualTo(1);
        } finally {
            container.stop();
        }
    }
}
