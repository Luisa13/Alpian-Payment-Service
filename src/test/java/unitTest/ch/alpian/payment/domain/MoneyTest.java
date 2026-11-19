package ch.alpian.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void creates_money_with_valid_currency_and_amount() {
        var money = new Money(new BigDecimal("100.00"), "CHF");
        assertThat(money.amount()).isEqualByComparingTo("100.00");
        assertThat(money.currency()).isEqualTo("CHF");
    }

    @Test
    void normalises_currency_to_uppercase() {
        var money = new Money(new BigDecimal("10.00"), "chf");
        assertThat(money.currency()).isEqualTo("CHF");
    }

    @Test
    void rejects_negative_amount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-1.00"), "CHF"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejects_null_amount() {
        assertThatThrownBy(() -> new Money(null, "CHF")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null_currency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1.00"), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_blank_currency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1.00"), "  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_unsupported_currency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1.00"), "XYZ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("XYZ");
    }

    @Test
    void rejects_too_many_decimal_places_for_chf() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1.001"), "CHF")).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void jpy_allows_zero_decimal_places() {
        var money = new Money(new BigDecimal("100"), "JPY");
        assertThat(money.amount()).isEqualByComparingTo("100");
    }

    @Test
    void add_same_currency() {
        var a = Money.of("10.00", "EUR");
        var b = Money.of("5.00", "EUR");
        assertThat(a.add(b).amount()).isEqualByComparingTo("15.00");
    }

    @Test
    void add_different_currency_throws() {
        assertThatThrownBy(() -> Money.of("10.00", "EUR").add(Money.of("5.00", "CHF")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subtract_same_currency() {
        var a = Money.of("10.00", "EUR");
        var b = Money.of("3.00", "EUR");
        assertThat(a.subtract(b).amount()).isEqualByComparingTo("7.00");
    }

    @Test
    void is_positive_true_for_non_zero() {
        assertThat(Money.of("0.01", "CHF").isPositive()).isTrue();
    }

    @Test
    void is_positive_false_for_zero() {
        assertThat(Money.of("0.00", "CHF").isPositive()).isFalse();
    }

    @Test
    void is_zero_true_for_zero() {
        assertThat(Money.of("0.00", "CHF").isZero()).isTrue();
    }

    @Test
    void is_zero_false_for_non_zero() {
        assertThat(Money.of("0.01", "CHF").isZero()).isFalse();
    }

    @Test
    void subtract_different_currency_throws() {
        assertThatThrownBy(() -> Money.of("10.00", "EUR").subtract(Money.of("5.00", "CHF")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zero_has_the_currency_scale() {
        assertThat(Money.zero("CHF").amount().scale()).isEqualTo(2);
        assertThat(Money.zero("CHF").isZero()).isTrue();
    }

    @Test
    void compares_amounts_of_the_same_currency() {
        var five = Money.of("5.00", "CHF");
        var ten = Money.of("10.00", "CHF");
        assertThat(five.isLessThan(ten)).isTrue();
        assertThat(ten.isLessThan(five)).isFalse();
        assertThat(ten.isGreaterThan(five)).isTrue();
        assertThat(ten.isGreaterThan(ten)).isFalse();
        assertThat(ten.isLessThan(ten)).isFalse();
    }

    @Test
    void comparing_different_currencies_throws() {
        assertThatThrownBy(() -> Money.of("1.00", "CHF").isLessThan(Money.of("1.00", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of("1.00", "CHF").isGreaterThan(Money.of("1.00", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subtracting_below_zero_is_refused() {
        assertThatThrownBy(() -> Money.of("1.00", "CHF").subtract(Money.of("2.00", "CHF")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void to_string_includes_amount_and_currency() {
        assertThat(Money.of("42.50", "USD").toString()).isEqualTo("42.50 USD");
    }
}
