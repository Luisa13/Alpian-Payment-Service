package ch.alpian.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final UUID SENDER = UUID.randomUUID();
    private static final Money TEN = Money.of("10.00", "CHF");

    private static Payment reserved() {
        return Payment.reserved("key-1", "h".repeat(64), SENDER, Receiver.internal(UUID.randomUUID()), TEN);
    }

    @Test
    void a_reserved_payment_has_a_reservation_time_and_is_not_final() {
        Payment p = reserved();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(p.getReservedAt()).isNotNull();
        assertThat(p.getFinalizedAt()).isNull();
    }

    @Test
    void a_rejected_payment_is_final_from_the_start_and_keeps_its_reason() {
        Payment p = Payment.rejected(
                "key-2",
                "h".repeat(64),
                SENDER,
                Receiver.external("CH9300762011623852957"),
                TEN,
                FailureReason.INSUFFICIENT_FUNDS);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(p.getFailureReason()).isEqualTo(FailureReason.INSUFFICIENT_FUNDS);
        assertThat(p.getReservedAt()).isNull();
        assertThat(p.getFinalizedAt()).isNotNull();
    }

    @Test
    void complete_stores_the_rail_reference() {
        Payment p = reserved();
        p.complete("REF-1");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(p.getRailReference()).isEqualTo("REF-1");
        assertThat(p.getFinalizedAt()).isNotNull();
    }

    @Test
    void fail_stores_the_reason() {
        Payment p = reserved();
        p.fail(FailureReason.RAIL_REJECTED);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.getFailureReason()).isEqualTo(FailureReason.RAIL_REJECTED);
    }

    @Test
    void a_completed_payment_cannot_change() {
        Payment p = reserved();
        p.complete("REF-1");
        assertThatThrownBy(() -> p.fail(FailureReason.RAIL_REJECTED)).isInstanceOf(InvalidPaymentStateException.class);
        assertThatThrownBy(() -> p.complete("REF-2")).isInstanceOf(InvalidPaymentStateException.class);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(p.getRailReference()).isEqualTo("REF-1");
    }

    @Test
    void a_failed_payment_cannot_change() {
        Payment p = reserved();
        p.fail(FailureReason.RAIL_REJECTED);
        assertThatThrownBy(() -> p.complete("REF")).isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void a_rejected_payment_cannot_change() {
        Payment p = Payment.rejected(
                "key-3",
                "h".repeat(64),
                SENDER,
                Receiver.internal(UUID.randomUUID()),
                TEN,
                FailureReason.INSUFFICIENT_FUNDS);
        assertThatThrownBy(() -> p.complete("REF")).isInstanceOf(InvalidPaymentStateException.class);
        assertThatThrownBy(() -> p.fail(FailureReason.RAIL_REJECTED)).isInstanceOf(InvalidPaymentStateException.class);
    }
}
