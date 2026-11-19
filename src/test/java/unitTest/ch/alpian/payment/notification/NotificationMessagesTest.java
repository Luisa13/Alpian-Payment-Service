package ch.alpian.payment.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.alpian.payment.application.events.PaymentEvents;
import ch.alpian.payment.application.events.PaymentPayload;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationMessagesTest {

    private static PaymentPayload internal(String status, String reason) {
        return new PaymentPayload(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "10.00", "CHF", status, reason);
    }

    private static PaymentPayload external(String status, String reason) {
        return new PaymentPayload(
                UUID.randomUUID(), UUID.randomUUID(), null, "CH9300762011623852957", "10.00", "CHF", status, reason);
    }

    @Test
    void every_event_type_has_a_message_mentioning_amount() {
        for (String type : new String[] {
            PaymentEvents.PAYMENT_RESERVED,
            PaymentEvents.PAYMENT_COMPLETED,
            PaymentEvents.PAYMENT_FAILED,
            PaymentEvents.PAYMENT_REJECTED
        }) {
            assertThat(NotificationMessages.describe(type, internal("X", "R"))).contains("10.00 CHF");
        }
    }

    @Test
    void mentions_the_iban_for_external_receivers() {
        assertThat(NotificationMessages.describe(PaymentEvents.PAYMENT_COMPLETED, external("COMPLETED", null)))
                .contains("IBAN CH9300762011623852957");
    }

    @Test
    void mentions_the_reason_for_rejections_and_failures() {
        assertThat(NotificationMessages.describe(
                        PaymentEvents.PAYMENT_REJECTED, internal("REJECTED", "INSUFFICIENT_FUNDS")))
                .contains("INSUFFICIENT_FUNDS");
        assertThat(NotificationMessages.describe(PaymentEvents.PAYMENT_FAILED, internal("FAILED", "RAIL_REJECTED")))
                .contains("RAIL_REJECTED");
    }

    @Test
    void unknown_event_type_is_a_poison_message() {
        assertThatThrownBy(() -> NotificationMessages.describe("PAYMENT_EXPLODED", internal("X", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
