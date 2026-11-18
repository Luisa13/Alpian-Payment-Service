package ch.example.template.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private Payment newPayment() {
        return Payment.create(new Money(new BigDecimal("100.00"), "CHF"), UUID.randomUUID(), UUID.randomUUID(), "Rent");
    }

    @Test
    void created_payment_is_in_requested_status() {
        var p = newPayment();
        assertThat(p.getPaymentStatus()).isInstanceOf(PaymentStatus.Requested.class);
        assertThat(p.getId()).isNotNull();
        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(p.getUpdatedAt()).isNotNull();
    }

    @Test
    void settle_transitions_to_settled() {
        var p = newPayment();
        p.settle();
        assertThat(p.getPaymentStatus()).isInstanceOf(PaymentStatus.Settled.class);
    }

    @Test
    void fail_transitions_to_failed() {
        var p = newPayment();
        p.fail("timeout");
        assertThat(p.getPaymentStatus()).isInstanceOf(PaymentStatus.Failed.class);
        assertThat(((PaymentStatus.Failed) p.getPaymentStatus()).reason()).isEqualTo("timeout");
    }

    @Test
    void cannot_settle_already_settled_payment() {
        var p = newPayment();
        p.settle();
        assertThatThrownBy(p::settle).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannot_fail_already_settled_payment() {
        var p = newPayment();
        p.settle();
        assertThatThrownBy(() -> p.fail("reason")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannot_settle_already_failed_payment() {
        var p = newPayment();
        p.fail("error");
        assertThatThrownBy(p::settle).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void get_amount_returns_money() {
        var amount = new Money(new BigDecimal("50.00"), "EUR");
        var p = Payment.create(amount, UUID.randomUUID(), UUID.randomUUID(), "test");
        assertThat(p.getAmount().amount()).isEqualByComparingTo("50.00");
        assertThat(p.getAmount().currency()).isEqualTo("EUR");
    }

    @Test
    void version_is_null_on_new_entity() {
        assertThat(newPayment().getVersion()).isNull();
    }
}
