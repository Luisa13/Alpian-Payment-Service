package ch.example.template.transfer.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.example.template.shared.domain.EventEnvelope;
import ch.example.template.shared.domain.Money;
import ch.example.template.transfer.application.TransferService;
import ch.example.template.transfer.domain.Transfer;
import ch.example.template.transfer.domain.TransferRepository;
import ch.example.template.transfer.domain.TransferStatus;
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

/**
 * Verifies consumer-side idempotency: producing the same event ID twice must result in exactly one
 * {@code transfers.settled} event and one state change in the DB.
 */
class ConsumerIdempotencyIT extends IntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void duplicate_event_id_is_processed_only_once() throws Exception {
        // ── Create a transfer directly in the DB ──────────────────────────────
        Transfer transfer = Transfer.create(
                new Money(new BigDecimal("10.00"), "CHF"), UUID.randomUUID(), UUID.randomUUID(), "dedup test");
        transferRepository.save(transfer);
        UUID transferId = transfer.getId();

        // ── Build the event payload ───────────────────────────────────────────
        UUID sharedEventId = UUID.randomUUID();
        var payload = new TransferService.TransferPayload(
                transferId, "10.00", "CHF", transfer.getSourceAccountId(), transfer.getTargetAccountId(), "dedup test");
        var envelope = new EventEnvelope<>(
                sharedEventId,
                TransferService.EVENT_TYPE_REQUESTED,
                TransferService.EVENT_VERSION,
                java.time.Instant.now(),
                UUID.randomUUID(),
                payload);
        String json = objectMapper.writeValueAsString(envelope);

        // ── Listen for settled events ─────────────────────────────────────────
        var settledRecords = Collections.synchronizedList(new java.util.ArrayList<ConsumerRecord<String, String>>());
        var containerProps = new ContainerProperties("transfers.settled");
        containerProps.setGroupId("test-dedup-" + UUID.randomUUID());
        containerProps.setMessageListener((MessageListener<String, String>) settledRecords::add);
        var container = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        container.start();
        ContainerTestUtils.waitForAssignment(container, 1);

        try {
            // ── Publish the same event twice ──────────────────────────────────
            kafkaTemplate.send(new ProducerRecord<>("transfers.requested", transferId.toString(), json));
            kafkaTemplate.send(new ProducerRecord<>("transfers.requested", transferId.toString(), json));
            kafkaTemplate.flush();

            // ── Await exactly one settled event ───────────────────────────────
            String transferKey = transferId.toString();
            await().atMost(Duration.ofSeconds(15))
                    .until(() -> settledRecords.stream()
                            .anyMatch(r -> r.value() != null
                                    && r.value().contains("transfer.settled")
                                    && transferKey.equals(r.key())));

            // Allow a moment for a potential duplicate to arrive
            Thread.sleep(500);

            long settledCount = settledRecords.stream()
                    .filter(r ->
                            r.value() != null && r.value().contains("transfer.settled") && transferKey.equals(r.key()))
                    .count();
            assertThat(settledCount).isEqualTo(1);

            // ── Transfer in DB must be SETTLED ────────────────────────────────
            Transfer settled = transferRepository.findById(transferId).orElseThrow();
            assertThat(settled.getTransferStatus()).isInstanceOf(TransferStatus.Settled.class);
        } finally {
            container.stop();
        }
    }
}
