package ch.example.template.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void creates_valid_money() {
        var m = new Money(new BigDecimal("100.00"), "CHF");
        assertThat(m.amount()).isEqualByComparingTo("100.00");
        assertThat(m.currency()).isEqualTo("CHF");
    }

    @Test
    void rejects_null_amount() {
        assertThatThrownBy(() -> new Money(null, "CHF")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null_currency() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_negative_amount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-1.00"), "CHF"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejects_unknown_currency() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "XXX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void rejects_wrong_scale_for_chf() {
        // CHF has 2 decimal places; 3 dp should be rejected
        assertThatThrownBy(() -> new Money(new BigDecimal("1.001"), "CHF")).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void zero_amount_is_valid() {
        var m = new Money(BigDecimal.ZERO.setScale(2), "EUR");
        assertThat(m.isZero()).isTrue();
        assertThat(m.isPositive()).isFalse();
    }

    @Test
    void positive_amount_is_positive() {
        var m = new Money(new BigDecimal("0.01"), "USD");
        assertThat(m.isPositive()).isTrue();
        assertThat(m.isZero()).isFalse();
    }

    @Test
    void add_same_currency() {
        var a = new Money(new BigDecimal("10.00"), "CHF");
        var b = new Money(new BigDecimal("5.50"), "CHF");
        assertThat(a.add(b).amount()).isEqualByComparingTo("15.50");
    }

    @Test
    void add_rejects_different_currencies() {
        var a = new Money(new BigDecimal("10.00"), "CHF");
        var b = new Money(new BigDecimal("5.00"), "EUR");
        assertThatThrownBy(() -> a.add(b)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subtract_same_currency() {
        var a = new Money(new BigDecimal("10.00"), "CHF");
        var b = new Money(new BigDecimal("3.00"), "CHF");
        assertThat(a.subtract(b).amount()).isEqualByComparingTo("7.00");
    }

    @Test
    void subtract_rejects_different_currencies() {
        var a = new Money(new BigDecimal("10.00"), "CHF");
        var b = new Money(new BigDecimal("3.00"), "USD");
        assertThatThrownBy(() -> a.subtract(b)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_factory_accepts_string_amount() {
        var m = Money.of("42.50", "GBP");
        assertThat(m.amount()).isEqualByComparingTo("42.50");
        assertThat(m.currency()).isEqualTo("GBP");
    }

    @Test
    void jpy_has_zero_decimal_places() {
        var m = new Money(new BigDecimal("1000"), "JPY");
        assertThat(m.amount()).isEqualByComparingTo("1000");
    }

    @Test
    void jpy_rejects_fractional_amount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1000.5"), "JPY")).isInstanceOf(ArithmeticException.class);
    }
}
