package ch.alpian.payment.domain.exception;

import ch.alpian.payment.domain.Money;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, Money requested, Money available) {
        super("Account " + accountId + " has " + available + " available, " + requested + " requested");
    }
}
