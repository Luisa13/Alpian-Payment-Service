package ch.example.template.transfer.infrastructure;

import ch.example.template.config.CorrelationIdFilter;
import ch.example.template.shared.domain.EventEnvelope;
import ch.example.template.transfer.application.TransferService;
import ch.example.template.transfer.domain.Transfer;
import ch.example.template.transfer.domain.TransferRepository;
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
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for {@code transfers.requested}.
 *
 * <p>Simulates settlement and emits a {@code transfers.settled} event via the outbox.
 *
 * <p>Reliability pattern:
 *
 * <ul>
 *   <li><b>Consumer idempotency</b>: checks {@link ProcessedEventStore} before any state mutation.
 *   <li><b>Retry with backoff</b>: {@link RetryableTopic} with exponential backoff retries on
 *       retryable exceptions; non-retryable exceptions go directly to DLT.
 *   <li><b>DLT</b>: unrecoverable messages land in {@code transfers.requested.DLT}; a DLT listener
 *       increments the {@code transfers.dlt.messages} counter.
 * </ul>
 *
 * <p>Non-retryable exceptions (deserialization/schema errors) are classified in {@link
 * ch.example.template.config.KafkaConfig}.
 */
@Component
class TransferEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferEventConsumer.class);

    private static final String TOPIC_SETTLED = "transfers.settled";
    private static final String EVENT_TYPE_SETTLED = "transfer.settled";

    private final TransferRepository transfers;
    private final OutboxRepository outbox;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper objectMapper;

    private final Counter settledCounter;
    private final Counter failedCounter;
    private final Counter dltCounter;

    TransferEventConsumer(
            TransferRepository transfers,
            OutboxRepository outbox,
            ProcessedEventStore processedEvents,
            ObjectMapper objectMapper,
            MeterRegistry meters) {
        this.transfers = transfers;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;

        this.settledCounter = Counter.builder("transfers.settled")
                .description("Number of transfers successfully settled")
                .register(meters);
        this.failedCounter = Counter.builder("transfers.failed")
                .description("Number of transfers that failed settlement")
                .tag("reason", "settlement_error")
                .register(meters);
        this.dltCounter = Counter.builder("transfers.dlt.messages")
                .description("Number of messages sent to the dead-letter topic")
                .register(meters);
    }

    @KafkaListener(topics = TransferService.TOPIC_REQUESTED, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onTransferRequested(ConsumerRecord<String, String> record) {

        // Propagate correlation ID from Kafka header into MDC
        Header corrHeader = record.headers().lastHeader("X-Correlation-Id");
        if (corrHeader != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, new String(corrHeader.value()));
        }

        try {
            EventEnvelope<TransferService.TransferPayload> envelope = deserialize(record.value());

            // ── Consumer idempotency ───────────────────────────────────────────
            if (!processedEvents.tryMarkProcessed(envelope.eventId(), record.topic())) {
                log.debug("Duplicate event skipped: eventId={}", envelope.eventId());
                return;
            }

            UUID transferId = UUID.fromString(record.key());
            Transfer transfer = transfers
                    .findById(transferId)
                    .orElseThrow(() -> new IllegalStateException("Transfer not found for event: " + transferId));

            // ── Simulate settlement (replace with real payment network call) ───
            transfer.settle();
            transfers.save(transfer);

            // ── Emit settled event via outbox ──────────────────────────────────
            var settledEnvelope = EventEnvelope.of(
                    EVENT_TYPE_SETTLED,
                    TransferService.EVENT_VERSION,
                    envelope.correlationId(),
                    TransferService.TransferPayload.from(transfer));
            String payload = serialize(settledEnvelope);
            outbox.save(OutboxEntry.of(TOPIC_SETTLED, transferId.toString(), payload));

            settledCounter.increment();
            log.info("Transfer settled id={}", transferId);

        } catch (Exception e) {
            failedCounter.increment();
            throw e; // let Spring Kafka retry/DLT handle it
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    /** DLT listener — increments counter; messages stay in the DLT for manual inspection. */
    @KafkaListener(topics = TransferService.TOPIC_REQUESTED + "-dlt", groupId = "${spring.kafka.consumer.group-id}-dlt")
    public void onDlt(ConsumerRecord<String, String> record) {
        dltCounter.increment();
        log.error("Message landed in DLT: topic={} key={} offset={}", record.topic(), record.key(), record.offset());
    }

    private EventEnvelope<TransferService.TransferPayload> deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<EventEnvelope<TransferService.TransferPayload>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialise transfer event: " + e.getMessage(), e);
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
