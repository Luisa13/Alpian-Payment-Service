package ch.alpian.payment.domain;

import java.util.UUID;

public class InvalidPaymentStateException extends RuntimeException {

    public InvalidPaymentStateException(UUID paymentId, PaymentStatus from, PaymentStatus to) {
        super("Payment " + paymentId + " cannot move from " + from + " to " + to);
    }
}
