package ch.example.template.application.flows;

import ch.example.template.application.tasks.CheckIdempotencyTask;
import ch.example.template.application.tasks.SavePaymentTask;
import ch.example.template.application.tasks.StoreIdempotencyResponseTask;
import ch.example.template.application.tasks.WriteOutboxTask;
import ch.example.template.domain.EventEnvelope;
import ch.example.template.domain.Money;
import ch.example.template.domain.Payment;
import ch.example.template.domain.PaymentStatus;
import ch.example.template.domain.PaymentStatus;
import ch.example.template.infrastructure.persistence.IdempotencyStore.StoredResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flow: initiate a new payment.
 *
 * <p>Orchestrates tasks in this order — all within a single transaction:
 * <ol>
 *   <li>{@link CheckIdempotencyTask} — short-circuit on replay
 *   <li>{@link SavePaymentTask} — persist the Payment aggregate
 *   <li>{@link WriteOutboxTask} — enqueue the domain event
 *   <li>{@link StoreIdempotencyResponseTask} — cache the response for future replays
 * </ol>
 */
@Service
public class CreatePaymentFlow {

    private static final Logger log = LoggerFactory.getLogger(CreatePaymentFlow.class);

    public static final String TOPIC_REQUESTED = "payments.requested";
    public static final String EVENT_TYPE_REQUESTED = "payment.requested";
    public static final int EVENT_VERSION = 1;

    private final CheckIdempotencyTask checkIdempotency;
    private final SavePaymentTask savePayment;
    private final WriteOutboxTask writeOutbox;
    private final StoreIdempotencyResponseTask storeIdempotencyResponse;
    private final ObjectMapper objectMapper;
    private final Counter createdCounter;

    public record Arguments(
            Money amount,
            UUID sourceAccountId,
            UUID targetAccountId,
            String description,
            String idempotencyKey,
            String requestHash) {}

    public CreatePaymentFlow(
            CheckIdempotencyTask checkIdempotency,
            SavePaymentTask savePayment,
            WriteOutboxTask writeOutbox,
            StoreIdempotencyResponseTask storeIdempotencyResponse,
            ObjectMapper objectMapper,
            MeterRegistry meters) {
        this.checkIdempotency = checkIdempotency;
        this.savePayment = savePayment;
        this.writeOutbox = writeOutbox;
        this.storeIdempotencyResponse = storeIdempotencyResponse;
        this.objectMapper = objectMapper;
        this.createdCounter = Counter.builder("payments.created")
                .description("Number of payments successfully initiated")
                .register(meters);
    }

    @Transactional
    public PaymentResult initiate(Arguments args) {
        // 1. Idempotency check — replay if key already exists
        var existing = checkIdempotency.execute(
                new CheckIdempotencyTask.Arguments(args.idempotencyKey(), args.requestHash()));
        if (existing.isPresent()) {
            StoredResponse stored = existing.get();
            log.debug("Idempotent replay for key={}", args.idempotencyKey());
            return PaymentResult.replay(stored);
        }

        // 2. Persist the Payment aggregate
        Payment payment = savePayment.execute(new SavePaymentTask.Arguments(
                args.amount(), args.sourceAccountId(), args.targetAccountId(), args.description()));

        // 3. Write outbox event
        UUID correlationId = correlationUuid();
        EventEnvelope<PaymentPayload> envelope =
                EventEnvelope.of(EVENT_TYPE_REQUESTED, EVENT_VERSION, correlationId, PaymentPayload.from(payment));
        writeOutbox.execute(new WriteOutboxTask.Arguments(
                TOPIC_REQUESTED, payment.getId().toString(), serialize(envelope)));

        // 4. Store idempotency response for future replays
        String responseBody = serialize(PaymentResponseDto.from(payment));
        storeIdempotencyResponse.execute(
                new StoreIdempotencyResponseTask.Arguments(args.idempotencyKey(), args.requestHash(), 201, responseBody));

        createdCounter.increment();
        log.info("Payment created id={} amount={}", payment.getId(), payment.getAmount());
        return PaymentResult.created(payment);
    }

    // -------------------------------------------------------------------------
    // Supporting types
    // -------------------------------------------------------------------------

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

    /**
     * Serialisable DTO stored in the idempotency table so the controller can replay
     * the identical JSON body on a repeated request.
     * Field names intentionally match the OpenAPI-generated {@code PaymentResponse} model.
     */
    public record PaymentResponseDto(
            UUID id,
            String amount,
            String currency,
            String status,
            String failureReason,
            UUID sourceAccountId,
            UUID targetAccountId,
            String description,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {

        public static PaymentResponseDto from(Payment p) {
            String failureReason = (p.getPaymentStatus() instanceof PaymentStatus.Failed f) ? f.reason() : null;
            return new PaymentResponseDto(
                    p.getId(),
                    p.getAmount().amount().toPlainString(),
                    p.getAmount().currency(),
                    p.getPaymentStatus().code(),
                    failureReason,
                    p.getSourceAccountId(),
                    p.getTargetAccountId(),
                    p.getDescription(),
                    p.getCreatedAt(),
                    p.getUpdatedAt());
        }
    }

    public sealed interface PaymentResult permits PaymentResult.Created, PaymentResult.Replayed {

        record Created(Payment payment) implements PaymentResult {}

        record Replayed(int responseStatus, String responseBody) implements PaymentResult {}

        static PaymentResult created(Payment p) { return new Created(p); }

        static PaymentResult replay(StoredResponse s) {
            return new Replayed(s.responseStatus(), s.responseBody());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UUID correlationUuid() {
        String mdcValue = MDC.get("correlationId");
        if (mdcValue != null) {
            try { return UUID.fromString(mdcValue); } catch (IllegalArgumentException ignored) {}
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
}
