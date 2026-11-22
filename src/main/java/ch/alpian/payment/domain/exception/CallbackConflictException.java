package ch.alpian.payment.domain.exception;

import ch.alpian.payment.domain.PaymentStatus;
import java.util.UUID;

/** Raised when a rail callback delivers an outcome that contradicts the payment's already final status. */
public class CallbackConflictException extends RuntimeException {

    private final UUID paymentId;

    public CallbackConflictException(UUID paymentId, PaymentStatus currentStatus, String incomingOutcome) {
        super("Payment " + paymentId + " is already " + currentStatus + " but callback reported " + incomingOutcome);
        this.paymentId = paymentId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }
}
