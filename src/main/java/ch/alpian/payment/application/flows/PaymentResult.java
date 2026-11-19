package ch.alpian.payment.application.flows;

import ch.alpian.payment.domain.Payment;

/**
 * Outcome of {@link CreatePaymentFlow}, mapped to an HTTP status by the API layer.
 *
 * @param payment the payment as it stands now
 * @param kind    how the request ended
 */
public record PaymentResult(Payment payment, Kind kind) {

    public enum Kind {
        /** Settled inside the request: {@code COMPLETED} or {@code FAILED}. HTTP 201. */
        CREATED,
        /** Funds held, settlement not confirmed yet ({@code RESERVED}). HTTP 202 + Location. */
        ACCEPTED,
        /** Refused at intake ({@code REJECTED}). HTTP 422. */
        REJECTED,
        /** Same idempotency key, same request: the stored payment is returned. HTTP 200. */
        REPLAYED
    }
}
