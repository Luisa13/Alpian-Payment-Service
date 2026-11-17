package ch.example.template.transfer.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.example.template.transfer.domain.TransferRepository;
import ch.example.template.transfer.domain.TransferStatus;
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

/**
 * Full end-to-end scenario:
 *
 * <ol>
 *   <li>POST /v1/transfers → 201
 *   <li>Outbox relay publishes {@code transfers.requested}
 *   <li>Consumer settles the transfer → publishes {@code transfers.settled}
 *   <li>DB reflects SETTLED status
 * </ol>
 */
class TransferEndToEndIT extends IntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void post_transfer_reaches_settled_state_end_to_end() throws Exception {
        // ── Listen for transfers.settled ──────────────────────────────────────
        var settledRecords = Collections.synchronizedList(new java.util.ArrayList<ConsumerRecord<String, String>>());
        var containerProps = new ContainerProperties("transfers.settled");
        containerProps.setGroupId("test-e2e-" + UUID.randomUUID());
        containerProps.setMessageListener((MessageListener<String, String>) settledRecords::add);
        var container = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        container.start();
        ContainerTestUtils.waitForAssignment(container, 1);

        try {
            // ── POST /v1/transfers ────────────────────────────────────────────
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

            var createResp = restTemplate.postForEntity("/v1/transfers", new HttpEntity<>(body, headers), String.class);
            assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            JsonNode responseJson = objectMapper.readTree(createResp.getBody());
            UUID transferId = UUID.fromString(responseJson.get("id").asText());

            // ── Await transfers.settled on Kafka ──────────────────────────────
            await().atMost(Duration.ofSeconds(20))
                    .until(() -> settledRecords.stream()
                            .anyMatch(r -> r.value() != null
                                    && r.value().contains("transfer.settled")
                                    && r.key().equals(transferId.toString())));

            // ── Verify Transfer is SETTLED in the DB ──────────────────────────
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var transfer = transferRepository.findById(transferId).orElseThrow();
                assertThat(transfer.getTransferStatus()).isInstanceOf(TransferStatus.Settled.class);
            });

            // ── Verify the settled event envelope ─────────────────────────────
            ConsumerRecord<String, String> settledEvent = settledRecords.stream()
                    .filter(r -> r.key().equals(transferId.toString()))
                    .findFirst()
                    .orElseThrow();
            JsonNode eventJson = objectMapper.readTree(settledEvent.value());
            assertThat(eventJson.get("type").asText()).isEqualTo("transfer.settled");
            assertThat(eventJson.get("version").asInt()).isEqualTo(1);
        } finally {
            container.stop();
        }
    }
}
