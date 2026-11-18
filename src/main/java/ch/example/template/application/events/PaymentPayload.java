package ch.example.template.application.events;

import ch.example.template.domain.Payment;
import java.util.UUID;

/**
 * Integration event payload emitted for payment lifecycle events.
 */
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
