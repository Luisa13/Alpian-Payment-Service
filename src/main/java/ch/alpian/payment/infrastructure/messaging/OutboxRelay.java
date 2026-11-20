package ch.alpian.payment.infrastructure.messaging;

import ch.alpian.payment.application.events.PaymentEvents;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional outbox relay.
 *
 * <p>Every {@code app.outbox.relay-interval-ms} it claims a batch of up to 100 unpublished rows
 * with {@code FOR UPDATE SKIP LOCKED} in a short TX1, releases the row lock, then sends each
 * record to Kafka outside any transaction. After a successful ack, TX2 marks the row published. On
 * failure the row's attempt counter and error are recorded and {@code claimed_at} is cleared so the
 * next relay cycle can retry immediately. After {@code MAX_PUBLISH_ATTEMPTS} failures the row is
 * dead-lettered ({@code dead_lettered_at} is set) and skipped by all future relay cycles. A row
 * that is claimed but never published (crash between TX1 and TX2) is reclaimed after {@code
 * app.outbox.claim-ttl-ms}. The Kafka key is {@code
 * payment_id}, guaranteeing partition affinity. A crash after ack but before TX2 can send a record
 * twice; the consumer dedupes on event id.
 */
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final int MAX_PUBLISH_ATTEMPTS = 5;

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate txTemplate;
    private final long ackTimeoutMs;
    private final long claimTtlMs;

    OutboxRelay(
            JdbcClient jdbc,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meters,
            PlatformTransactionManager txManager,
            @Value("${app.outbox.ack-timeout-ms}") long ackTimeoutMs,
            @Value("${app.outbox.claim-ttl-ms}") long claimTtlMs) {
        this.jdbc = jdbc;
        this.kafkaTemplate = kafkaTemplate;
        this.txTemplate = new TransactionTemplate(txManager);
        this.ackTimeoutMs = ackTimeoutMs;
        this.claimTtlMs = claimTtlMs;

        Gauge.builder("outbox.backlog.events", this, OutboxRelay::backlog)
                .description("Number of unpublished outbox events waiting for Kafka")
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms}")
    public void relay() {
        List<OutboxRow> batch = txTemplate.execute(status -> fetchAndClaim());
        if (batch == null || batch.isEmpty()) return;

        for (OutboxRow row : batch) {
            try {
                send(row);
                txTemplate.executeWithoutResult(status -> markPublished(row));
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                txTemplate.executeWithoutResult(status -> markFailed(row, e));
                log.warn(
                        "Outbox event {} not published (attempt recorded, dead-lettered after {}): {}",
                        row.id(),
                        MAX_PUBLISH_ATTEMPTS,
                        e.toString());
                return;
            }
        }
    }

    private List<OutboxRow> fetchAndClaim() {
        Timestamp claimCutoff = new Timestamp(System.currentTimeMillis() - claimTtlMs);
        List<OutboxRow> batch = jdbc.sql("""
                        SELECT id, aggregate_id, event_type, payload::text AS payload
                        FROM outbox_events
                        WHERE published_at IS NULL
                          AND dead_lettered_at IS NULL
                          AND (claimed_at IS NULL OR claimed_at < :claimCutoff)
                        ORDER BY created_at
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("claimCutoff", claimCutoff)
                .param("limit", BATCH_SIZE)
                .query((rs, n) -> new OutboxRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload")))
                .list();

        if (!batch.isEmpty()) {
            List<UUID> ids = batch.stream().map(OutboxRow::id).toList();
            jdbc.sql("UPDATE outbox_events SET claimed_at = now() WHERE id IN (:ids)")
                    .param("ids", ids)
                    .update();
        }
        return batch;
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
                        SET publish_attempts = publish_attempts + 1,
                            last_error = :error,
                            claimed_at = NULL,
                            dead_lettered_at = CASE
                                WHEN publish_attempts + 1 >= :maxAttempts THEN now()
                                ELSE NULL
                            END
                        WHERE id = :id
                        """)
                .param("error", message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH)))
                .param("maxAttempts", MAX_PUBLISH_ATTEMPTS)
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
