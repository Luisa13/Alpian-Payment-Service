package ch.example.template.application;

import ch.example.template.domain.EntityNotFoundException;
import ch.example.template.domain.EventEnvelope;
import ch.example.template.domain.Money;
import ch.example.template.domain.Payment;
import ch.example.template.domain.PaymentRepository;
import ch.example.template.infrastructure.persistence.IdempotencyStore;
import ch.example.template.infrastructure.persistence.OutboxEntry;
import ch.example.template.infrastructure.persistence.OutboxRepository;
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
 * Orchestrates the payment creation flow.
 *
 * <p>Idempotency + outbox are written atomically in one transaction:
 *
 * <pre>
 *   BEGIN
 *     INSERT payments
 *     INSERT outbox_events
 *     INSERT idempotency_keys
 *   COMMIT
 * </pre>
 *
 * The {@link ch.example.template.infrastructure.messaging.OutboxRelay} picks up the outbox row
 * asynchronously and publishes it to Kafka.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public static final String TOPIC_REQUESTED = "payments.requested";
    public static final String EVENT_TYPE_REQUESTED = "payment.requested";
    public static final int EVENT_VERSION = 1;

    private final PaymentRepository payments;
    private final OutboxRepository outbox;
    private final IdempotencyStore idempotency;
    private final ObjectMapper objectMapper;

    private final Counter createdCounter;

    public PaymentService(
            PaymentRepository payments,
            OutboxRepository outbox,
            IdempotencyStore idempotency,
            ObjectMapper objectMapper,
            MeterRegistry meters) {
        this.payments = payments;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.createdCounter = Counter.builder("payments.created")
                .description("Number of payments successfully initiated")
                .register(meters);
    }

    @Transactional
    public PaymentResult create(CreatePaymentCommand cmd, String idempotencyKey, String requestHash) {

        var existing = idempotency.findExisting(idempotencyKey, requestHash);
        if (existing.isPresent()) {
            log.debug("Idempotent replay for key={}", idempotencyKey);
            return PaymentResult.replay(existing.get());
        }

        Payment payment = Payment.create(cmd.amount(), cmd.sourceAccountId(), cmd.targetAccountId(), cmd.description());
        payments.save(payment);

        UUID correlationId = correlationUuid();
        EventEnvelope<PaymentPayload> envelope =
                EventEnvelope.of(EVENT_TYPE_REQUESTED, EVENT_VERSION, correlationId, PaymentPayload.from(payment));
        String payloadJson = serialize(envelope);
        outbox.save(OutboxEntry.of(TOPIC_REQUESTED, payment.getId().toString(), payloadJson));

        String responseBody = serialize(toResponse(payment));
        idempotency.storeResponse(idempotencyKey, requestHash, 201, responseBody);

        createdCounter.increment();

        log.info("Payment created id={} amount={}", payment.getId(), payment.getAmount());
        return PaymentResult.created(payment);
    }

    public Payment findById(UUID id) {
        return payments.findById(id).orElseThrow(() -> new EntityNotFoundException("Payment", id));
    }

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

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getAmount().amount().toPlainString(),
                p.getAmount().currency(),
                p.getPaymentStatus().code(),
                null,
                p.getSourceAccountId(),
                p.getTargetAccountId(),
                p.getDescription(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

    public record CreatePaymentCommand(Money amount, UUID sourceAccountId, UUID targetAccountId, String description) {}

    public record PaymentPayload(
            UUID paymentId,
            String amount,
            String currency,
            UUID sourceAccountId,
            UUID targetAccountId,
            String description) {

        public static PaymentPayload from(Payment p) {
            return new PaymentPayload(
                    p.getId(),
                    p.getAmount().amount().toPlainString(),
                    p.getAmount().currency(),
                    p.getSourceAccountId(),
                    p.getTargetAccountId(),
                    p.getDescription());
        }
    }

    public record PaymentResponse(
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

    public sealed interface PaymentResult permits PaymentResult.Created, PaymentResult.Replayed {

        record Created(Payment payment) implements PaymentResult {}

        record Replayed(int responseStatus, String responseBody) implements PaymentResult {}

        static PaymentResult created(Payment p) {
            return new Created(p);
        }

        static PaymentResult replay(IdempotencyStore.StoredResponse s) {
            return new Replayed(s.responseStatus(), s.responseBody());
        }
    }
}
