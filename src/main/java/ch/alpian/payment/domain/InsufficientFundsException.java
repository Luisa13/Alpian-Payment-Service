package ch.alpian.payment.domain;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, Money requested, Money available) {
        super("Account " + accountId + " has " + available + " available, " + requested + " requested");
    }
}
