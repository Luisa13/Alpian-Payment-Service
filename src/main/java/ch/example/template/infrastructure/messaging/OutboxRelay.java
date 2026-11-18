package ch.example.template.infrastructure.messaging;

import ch.example.template.api.CorrelationIdFilter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polling transactional outbox relay.
 *
 * <p>Uses {@code SELECT … FOR UPDATE SKIP LOCKED} to safely process a batch of unsent outbox rows,
 * publish each to Kafka, then mark them sent in one batched UPDATE.
 */
@Component
class OutboxRelay {

    private static final int BATCH_SIZE = 100;
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AtomicLong backlogGauge = new AtomicLong(0);

    @Value("${app.outbox.relay-interval-ms:1000}")
    private long relayIntervalMs;

    OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafkaTemplate, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.kafkaTemplate = kafkaTemplate;

        Gauge.builder("outbox.backlog.events", backlogGauge, AtomicLong::get)
                .description("Number of unsent outbox events waiting to be published to Kafka")
                .register(meters);
    }

    @PostConstruct
    void initBacklogGauge() {
        refreshBacklog();
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:1000}")
    @Transactional
    public void relay() {
        List<OutboxRow> batch =
                jdbc.sql("""
                        SELECT id, topic, message_key, payload
                        FROM outbox_events
                        WHERE sent = false
                        ORDER BY created_at ASC
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED
                        """).param("limit", BATCH_SIZE).query(OutboxRow.class).list();

        if (batch.isEmpty()) {
            backlogGauge.set(0);
            return;
        }

        for (OutboxRow row : batch) {
            var headers = new RecordHeaders();
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null) {
                headers.add(CORRELATION_HEADER, correlationId.getBytes());
            }

            var record = new ProducerRecord<String, String>(
                    row.topic(), null, null, row.messageKey(), row.payload(), headers);
            kafkaTemplate.send(record);
        }

        java.sql.Timestamp sentAt = java.sql.Timestamp.from(Instant.now());
        for (OutboxRow row : batch) {
            jdbc.sql("UPDATE outbox_events SET sent = true, sent_at = :sentAt WHERE id = CAST(:id AS uuid)")
                    .param("sentAt", sentAt)
                    .param("id", row.id().toString())
                    .update();
        }

        refreshBacklog();
    }

    private void refreshBacklog() {
        Long count = jdbc.sql("SELECT COUNT(*) FROM outbox_events WHERE sent = false")
                .query(Long.class)
                .single();
        backlogGauge.set(count != null ? count : 0);
    }

    record OutboxRow(java.util.UUID id, String topic, String messageKey, String payload) {}
}
