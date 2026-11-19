package ch.alpian.payment.domain;

public enum AccountType {
    CUSTOMER,
    /** Counterpart account that receives the money of payments leaving through the external rail. */
    CLEARING
}
