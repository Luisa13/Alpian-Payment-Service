package ch.alpian.payment.domain;

import java.util.UUID;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(UUID accountId, String accountCurrency, String paymentCurrency) {
        super("Account " + accountId + " holds " + accountCurrency + " but the payment is in " + paymentCurrency);
    }
}
