package ch.alpian.payment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public record Money(BigDecimal amount, String currency) {

    private static final Map<String, Integer> SCALE_BY_CURRENCY = Map.ofEntries(
            Map.entry("CHF", 2),
            Map.entry("EUR", 2),
            Map.entry("USD", 2),
            Map.entry("GBP", 2),
            Map.entry("JPY", 0),
            Map.entry("KWD", 3),
            Map.entry("BHD", 3),
            Map.entry("OMR", 3),
            Map.entry("TND", 3));

    public Money {
        if (amount == null) throw new IllegalArgumentException("amount must not be null");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");

        String upperCurrency = currency.toUpperCase();
        Integer scale = SCALE_BY_CURRENCY.get(upperCurrency);
        if (scale == null) throw new IllegalArgumentException("Unsupported ISO 4217 currency: " + currency);

        BigDecimal scaled = amount.setScale(scale, RoundingMode.UNNECESSARY);
        if (scaled.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("amount must not be negative: " + amount);

        amount = scaled;
        currency = upperCurrency;
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot operate on different currencies: " + this.currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
