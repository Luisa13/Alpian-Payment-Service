package ch.example.template.transfer.application;

import ch.example.template.shared.domain.EntityNotFoundException;
import ch.example.template.shared.domain.EventEnvelope;
import ch.example.template.shared.domain.Money;
import ch.example.template.shared.infrastructure.IdempotencyStore;
import ch.example.template.transfer.domain.Transfer;
import ch.example.template.transfer.domain.TransferRepository;
import ch.example.template.transfer.infrastructure.OutboxEntry;
import ch.example.template.transfer.infrastructure.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the transfer creation flow.
 *
 * <p>Idempotency + outbox are written atomically in one transaction:
 *
 * <pre>
 *   BEGIN
 *     INSERT transfers
 *     INSERT outbox_events
 *     INSERT idempotency_keys
 *   COMMIT
 * </pre>
 *
 * The {@link ch.example.template.transfer.infrastructure.OutboxRelay} picks up the outbox row
 * asynchronously and publishes it to Kafka.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    public static final String TOPIC_REQUESTED = "transfers.requested";
    public static final String EVENT_TYPE_REQUESTED = "transfer.requested";
    public static final int EVENT_VERSION = 1;

    private final TransferRepository transfers;
    private final OutboxRepository outbox;
    private final IdempotencyStore idempotency;
    private final ObjectMapper objectMapper;

    private final Counter createdCounter;

    public TransferService(
            TransferRepository transfers,
            OutboxRepository outbox,
            IdempotencyStore idempotency,
            ObjectMapper objectMapper,
            MeterRegistry meters) {
        this.transfers = transfers;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.createdCounter = Counter.builder("transfers.created")
                .description("Number of transfers successfully initiated")
                .register(meters);
    }

    /** Creates a new transfer or replays the original response for a duplicate idempotency key. */
    @Transactional
    public TransferResult create(CreateTransferCommand cmd, String idempotencyKey, String requestHash) {

        // ── Idempotency check ──────────────────────────────────────────────────
        var existing = idempotency.findExisting(idempotencyKey, requestHash);
        if (existing.isPresent()) {
            log.debug("Idempotent replay for key={}", idempotencyKey);
            return TransferResult.replay(existing.get());
        }

        // ── Business logic ────────────────────────────────────────────────────
        Transfer transfer =
                Transfer.create(cmd.amount(), cmd.sourceAccountId(), cmd.targetAccountId(), cmd.description());
        transfers.save(transfer);

        // ── Outbox ────────────────────────────────────────────────────────────
        UUID correlationId = correlationUuid();
        EventEnvelope<TransferPayload> envelope =
                EventEnvelope.of(EVENT_TYPE_REQUESTED, EVENT_VERSION, correlationId, TransferPayload.from(transfer));
        String payloadJson = serialize(envelope);
        outbox.save(OutboxEntry.of(TOPIC_REQUESTED, transfer.getId().toString(), payloadJson));

        // ── Store idempotency response ─────────────────────────────────────────
        String responseBody = serialize(toResponse(transfer));
        idempotency.storeResponse(idempotencyKey, requestHash, 201, responseBody);

        createdCounter.increment();

        log.info("Transfer created id={} amount={}", transfer.getId(), transfer.getAmount());
        return TransferResult.created(transfer);
    }

    public Transfer findById(UUID id) {
        return transfers.findById(id).orElseThrow(() -> new EntityNotFoundException("Transfer", id));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID correlationUuid() {
        String mdcValue = MDC.get("correlationId");
        if (mdcValue != null) {
            try {
                return UUID.fromString(mdcValue);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UUID.randomUUID();
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise object", e);
        }
    }

    private TransferResponse toResponse(Transfer t) {
        return new TransferResponse(
                t.getId(),
                t.getAmount().amount().toPlainString(),
                t.getAmount().currency(),
                t.getTransferStatus().code(),
                null,
                t.getSourceAccountId(),
                t.getTargetAccountId(),
                t.getDescription(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    public record CreateTransferCommand(Money amount, UUID sourceAccountId, UUID targetAccountId, String description) {}

    /**
     * Kafka event payload for {@code transfers.requested}. Versioned independently of the HTTP
     * response schema.
     */
    public record TransferPayload(
            UUID transferId,
            String amount,
            String currency,
            UUID sourceAccountId,
            UUID targetAccountId,
            String description) {

        public static TransferPayload from(Transfer t) {
            return new TransferPayload(
                    t.getId(),
                    t.getAmount().amount().toPlainString(),
                    t.getAmount().currency(),
                    t.getSourceAccountId(),
                    t.getTargetAccountId(),
                    t.getDescription());
        }
    }

    /** HTTP response DTO — also stored verbatim in the idempotency table. */
    public record TransferResponse(
            UUID id,
            String amount,
            String currency,
            String status,
            String failureReason,
            UUID sourceAccountId,
            UUID targetAccountId,
            String description,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {}

    /** Discriminated result: brand-new creation vs idempotent replay. */
    public sealed interface TransferResult permits TransferResult.Created, TransferResult.Replayed {

        record Created(Transfer transfer) implements TransferResult {}

        record Replayed(int responseStatus, String responseBody) implements TransferResult {}

        static TransferResult created(Transfer t) {
            return new Created(t);
        }

        static TransferResult replay(IdempotencyStore.StoredResponse s) {
            return new Replayed(s.responseStatus(), s.responseBody());
        }
    }
}
