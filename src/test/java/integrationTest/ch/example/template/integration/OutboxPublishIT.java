package ch.example.template.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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

class OutboxPublishIT extends IntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    @Test
    void post_payment_publishes_payments_requested_event() throws Exception {
        var received = Collections.synchronizedList(new java.util.ArrayList<ConsumerRecord<String, String>>());

        var containerProps = new ContainerProperties("payments.requested");
        containerProps.setGroupId("test-outbox-" + UUID.randomUUID());
        containerProps.setMessageListener((MessageListener<String, String>) received::add);

        var container = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        container.start();
        ContainerTestUtils.waitForAssignment(container, 1);

        try {
            String idempotencyKey = UUID.randomUUID().toString();
            String body = """
                    {
                      "amount": "25.00",
                      "currency": "CHF",
                      "sourceAccountId": "c0000000-0000-0000-0000-000000000001",
                      "targetAccountId": "c0000000-0000-0000-0000-000000000002",
                      "description": "outbox test"
                    }
                    """;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotency-Key", idempotencyKey);

            var resp = restTemplate.postForEntity("/v1/payments", new HttpEntity<>(body, headers), String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            await().atMost(Duration.ofSeconds(10))
                    .until(() -> received.stream()
                            .anyMatch(r -> r.value() != null && r.value().contains("payment.requested")));

            ConsumerRecord<String, String> event = received.stream()
                    .filter(r -> r.value() != null && r.value().contains("payment.requested"))
                    .findFirst()
                    .orElseThrow();

            assertThat(event.topic()).isEqualTo("payments.requested");
            assertThat(event.value()).contains("\"type\":\"payment.requested\"");
            assertThat(event.value()).contains("\"version\":1");
        } finally {
            container.stop();
        }
    }
}
