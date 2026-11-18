package ch.example.template.application.flows;

import ch.example.template.domain.Payment;
import ch.example.template.infrastructure.persistence.IdempotencyStore.StoredResponse;

/**
 * Outcome of {@link CreatePaymentFlow}: either a newly created {@link Payment}
 * or a previously stored idempotent response to replay.
 */
public sealed interface PaymentResult permits PaymentResult.Created, PaymentResult.Replayed {

    record Created(Payment payment) implements PaymentResult {}

    record Replayed(int responseStatus, String responseBody) implements PaymentResult {}

    static PaymentResult created(Payment p) {
        return new Created(p);
    }

    static PaymentResult replay(StoredResponse s) {
        return new Replayed(s.responseStatus(), s.responseBody());
    }
}
