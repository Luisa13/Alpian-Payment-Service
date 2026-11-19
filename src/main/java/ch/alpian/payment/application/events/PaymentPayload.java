package ch.alpian.payment.application.events;

import ch.alpian.payment.domain.Payment;
import java.util.UUID;

/** Integration event payload emitted for every payment state change. */
public record PaymentPayload(
        UUID paymentId,
        UUID senderAccountId,
        UUID receiverAccountId,
        String receiverIban,
        String amount,
        String currency,
        String status,
        String failureReason) {

    public static PaymentPayload from(Payment p) {
        return new PaymentPayload(
                p.getId(),
                p.getSenderAccountId(),
                p.getReceiver().accountId(),
                p.getReceiver().iban(),
                p.getAmount().amount().toPlainString(),
                p.getAmount().currency(),
                p.getStatus().name(),
                p.getFailureReason() == null ? null : p.getFailureReason().name());
    }
}
