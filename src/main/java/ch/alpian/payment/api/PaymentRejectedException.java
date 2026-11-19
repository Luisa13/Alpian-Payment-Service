package ch.alpian.payment.api;

import ch.alpian.payment.domain.FailureReason;
import java.util.UUID;

/** A payment was rejected at intake. Mapped to HTTP 422 carrying the payment id. */
public class PaymentRejectedException extends RuntimeException {

    private final UUID paymentId;
    private final FailureReason reason;

    public PaymentRejectedException(UUID paymentId, FailureReason reason) {
        super("Payment " + paymentId + " was rejected: " + reason);
        this.paymentId = paymentId;
        this.reason = reason;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public FailureReason getReason() {
        return reason;
    }
}
