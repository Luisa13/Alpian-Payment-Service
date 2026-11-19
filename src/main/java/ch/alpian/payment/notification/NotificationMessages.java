package ch.alpian.payment.notification;

import ch.alpian.payment.application.events.PaymentEvents;
import ch.alpian.payment.application.events.PaymentPayload;

/** Human-readable text of the notification sent to the payment's sender. */
final class NotificationMessages {

    private NotificationMessages() {}

    /** @throws IllegalArgumentException for an unknown event type (a poison message) */
    static String describe(String eventType, PaymentPayload p) {
        String money = p.amount() + " " + p.currency();
        String receiver = p.receiverIban() != null ? "IBAN " + p.receiverIban() : "account " + p.receiverAccountId();
        return switch (eventType) {
            case PaymentEvents.PAYMENT_RESERVED ->
                "Your payment of " + money + " to " + receiver + " was received; the funds are on hold.";
            case PaymentEvents.PAYMENT_COMPLETED -> "Your payment of " + money + " to " + receiver + " is complete.";
            case PaymentEvents.PAYMENT_FAILED ->
                "Your payment of " + money + " to " + receiver + " failed (" + p.failureReason()
                        + "); the funds are available again.";
            case PaymentEvents.PAYMENT_REJECTED ->
                "Your payment of " + money + " to " + receiver + " was rejected (" + p.failureReason() + ").";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
