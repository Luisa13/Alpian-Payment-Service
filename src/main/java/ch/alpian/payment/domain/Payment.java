package ch.alpian.payment.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Payment aggregate root. Pure domain object: persistence lives in infrastructure.
 *
 * <p>State changes are validated here through {@link PaymentStatus#canTransitionTo}; the repository
 * additionally applies them as guarded updates so two actors can never apply the same transition.
 */
public class Payment {

    private final UUID id;
    private final String idempotencyKey;
    private final String requestHash;
    private final UUID senderAccountId;
    private final Receiver receiver;
    private final Money amount;
    private PaymentStatus status;
    private FailureReason failureReason;
    private String railReference;
    private final int settlementAttempts;
    private final long version;
    private final Instant reservedAt;
    private Instant finalizedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Payment(
            UUID id,
            String idempotencyKey,
            String requestHash,
            UUID senderAccountId,
            Receiver receiver,
            Money amount,
            PaymentStatus status,
            FailureReason failureReason,
            String railReference,
            int settlementAttempts,
            long version,
            Instant reservedAt,
            Instant finalizedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.senderAccountId = senderAccountId;
        this.receiver = receiver;
        this.amount = amount;
        this.status = status;
        this.failureReason = failureReason;
        this.railReference = railReference;
        this.settlementAttempts = settlementAttempts;
        this.version = version;
        this.reservedAt = reservedAt;
        this.finalizedAt = finalizedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** New payment whose funds have been held on the sender. */
    public static Payment reserved(
            String idempotencyKey, String requestHash, UUID senderAccountId, Receiver receiver, Money amount) {
        Instant now = Instant.now();
        return new Payment(
                UUID.randomUUID(),
                idempotencyKey,
                requestHash,
                senderAccountId,
                receiver,
                amount,
                PaymentStatus.RESERVED,
                null,
                null,
                0,
                0,
                now,
                null,
                now,
                now);
    }

    /** New payment refused at intake. Nothing was held; it is final from the start. */
    public static Payment rejected(
            String idempotencyKey,
            String requestHash,
            UUID senderAccountId,
            Receiver receiver,
            Money amount,
            FailureReason reason) {
        Instant now = Instant.now();
        return new Payment(
                UUID.randomUUID(),
                idempotencyKey,
                requestHash,
                senderAccountId,
                receiver,
                amount,
                PaymentStatus.REJECTED,
                reason,
                null,
                0,
                0,
                null,
                now,
                now,
                now);
    }

    /** Rebuilds a payment from storage. */
    public static Payment restore(
            UUID id,
            String idempotencyKey,
            String requestHash,
            UUID senderAccountId,
            Receiver receiver,
            Money amount,
            PaymentStatus status,
            FailureReason failureReason,
            String railReference,
            int settlementAttempts,
            long version,
            Instant reservedAt,
            Instant finalizedAt,
            Instant createdAt,
            Instant updatedAt) {
        return new Payment(
                id,
                idempotencyKey,
                requestHash,
                senderAccountId,
                receiver,
                amount,
                status,
                failureReason,
                railReference,
                settlementAttempts,
                version,
                reservedAt,
                finalizedAt,
                createdAt,
                updatedAt);
    }

    public void complete(String railReference) {
        transitionTo(PaymentStatus.COMPLETED);
        this.railReference = railReference;
    }

    public void fail(FailureReason reason) {
        transitionTo(PaymentStatus.FAILED);
        this.failureReason = reason;
    }

    private void transitionTo(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidPaymentStateException(id, status, next);
        }
        this.status = next;
        this.finalizedAt = Instant.now();
        this.updatedAt = this.finalizedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getSenderAccountId() {
        return senderAccountId;
    }

    public Receiver getReceiver() {
        return receiver;
    }

    public Money getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public String getRailReference() {
        return railReference;
    }

    public int getSettlementAttempts() {
        return settlementAttempts;
    }

    public long getVersion() {
        return version;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
