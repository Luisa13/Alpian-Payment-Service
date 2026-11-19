package ch.alpian.payment.domain.exception;

import java.util.UUID;

/** A payment refers to an account that does not exist. */
public class UnknownAccountException extends RuntimeException {

    public UnknownAccountException(UUID accountId) {
        super("Account not found: " + accountId);
    }
}
