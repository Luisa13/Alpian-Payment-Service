package ch.alpian.payment.application.events;

import ch.alpian.payment.domain.PaymentStatus;

/** Kafka topic, event types and schema version for payment notifications. */
public final class PaymentEvents {

    public static final String TOPIC = "payment-notifications";
    public static final String DLT_TOPIC = TOPIC + ".DLT";

    public static final String PAYMENT_RESERVED = "PAYMENT_RESERVED";
    public static final String PAYMENT_REJECTED = "PAYMENT_REJECTED";
    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";

    public static final int EVENT_VERSION = 1;

    private PaymentEvents() {}

    /** One event per state change: the event type that announces a payment entering {@code status}. */
    public static String typeFor(PaymentStatus status) {
        return switch (status) {
            case RESERVED -> PAYMENT_RESERVED;
            case REJECTED -> PAYMENT_REJECTED;
            case COMPLETED -> PAYMENT_COMPLETED;
            case FAILED -> PAYMENT_FAILED;
        };
    }
}
