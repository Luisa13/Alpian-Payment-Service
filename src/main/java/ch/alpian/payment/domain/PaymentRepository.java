package ch.alpian.payment.domain;

import java.util.Optional;
import java.util.UUID;

/** Port: storage of payments. Implementations must apply transitions as guarded updates. */
public interface PaymentRepository {

    /**
     * Inserts a new payment.
     *
     * @throws DuplicateIdempotencyKeyException if the idempotency key is already used
     */
    void insert(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Writes the payment's new status, failure reason and rail reference only if it is still in
     * {@code from} ({@code UPDATE ... WHERE id = :id AND status = :from}).
     *
     * @return {@code false} when someone else already moved the payment; the caller must reload it
     */
    boolean applyTransition(Payment payment, PaymentStatus from);

    /** Counts one settlement attempt that ended UNKNOWN. Only touches a payment still RESERVED. */
    void recordSettlementAttempt(UUID paymentId);
}
