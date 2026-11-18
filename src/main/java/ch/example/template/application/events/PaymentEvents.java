package ch.example.template.application.events;

/**
 * Shared Kafka topic, event-type, and schema-version constants for payment events.
 */
public final class PaymentEvents {

    public static final String TOPIC_REQUESTED = "payments.requested";
    public static final String EVENT_TYPE_REQUESTED = "payment.requested";

    public static final String TOPIC_SETTLED = "payments.settled";
    public static final String EVENT_TYPE_SETTLED = "payment.settled";

    public static final int EVENT_VERSION = 1;

    private PaymentEvents() {}
}
