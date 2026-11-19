package ch.alpian.payment.infrastructure.messaging;

import ch.alpian.payment.application.events.PaymentEvents;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox relay.
 *
 * <p>Every {@code app.outbox.relay-interval-ms} (default 500) it locks a batch of up to 100
 * unpublished rows with {@code FOR UPDATE SKIP LOCKED}, so several instances never take the same
 * rows. Each row is sent with key = payment id and marked published only after Kafka acked it
 * ({@code acks=all}, idempotent producer). On the first failure the row's attempt counter and error
 * are recorded and the batch stops, which keeps events in order; the backlog drains when Kafka is
 * back. A crash between ack and commit can send a record twice; the consumer dedupes on event id.
 */
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ERROR_LENGTH = 500;

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long ackTimeoutMs;

    OutboxRelay(
            JdbcClient jdbc,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meters,
            @Value("${app.outbox.ack-timeout-ms:15000}") long ackTimeoutMs) {
        this.jdbc = jdbc;
        this.kafkaTemplate = kafkaTemplate;
        this.ackTimeoutMs = ackTimeoutMs;

        Gauge.builder("outbox.backlog.events", this, OutboxRelay::backlog)
                .description("Number of unpublished outbox events waiting for Kafka")
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxRow> batch = jdbc.sql("""
                        SELECT id, aggregate_id, event_type, payload::text AS payload
                        FROM outbox_events
                        WHERE published_at IS NULL
                        ORDER BY created_at
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("limit", BATCH_SIZE)
                .query((rs, n) -> new OutboxRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload")))
                .list();

        for (OutboxRow row : batch) {
            try {
                send(row);
                markPublished(row);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                markFailed(row, e);
                log.warn("Outbox event {} not published, will retry: {}", row.id(), e.toString());
                return;
            }
        }
    }

    private void send(OutboxRow row) throws InterruptedException, ExecutionException, TimeoutException {
        var headers = new RecordHeaders();
        headers.add("eventId", row.id().toString().getBytes(StandardCharsets.UTF_8));
        headers.add("eventType", row.eventType().getBytes(StandardCharsets.UTF_8));
        var record = new ProducerRecord<>(
                PaymentEvents.TOPIC, null, null, row.aggregateId().toString(), row.payload(), headers);
        kafkaTemplate.send(record).get(ackTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private void markPublished(OutboxRow row) {
        jdbc.sql("UPDATE outbox_events SET published_at = now() WHERE id = :id")
                .param("id", row.id())
                .update();
    }

    private void markFailed(OutboxRow row, Exception error) {
        String message = String.valueOf(error);
        jdbc.sql("""
                        UPDATE outbox_events
                        SET publish_attempts = publish_attempts + 1, last_error = :error
                        WHERE id = :id
                        """)
                .param("error", message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH)))
                .param("id", row.id())
                .update();
    }

    private double backlog() {
        Long count = jdbc.sql("SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL")
                .query(Long.class)
                .single();
        return count == null ? 0 : count;
    }

    record OutboxRow(UUID id, UUID aggregateId, String eventType, String payload) {}
}
