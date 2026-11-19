package ch.alpian.payment.domain;

import java.util.UUID;

/** Where the money goes: one of our accounts, or an external IBAN. Exactly one of the two is set. */
public record Receiver(ReceiverType type, UUID accountId, String iban) {

    public Receiver {
        if (type == null) throw new IllegalArgumentException("receiver type must not be null");
        boolean internalShape = type == ReceiverType.INTERNAL && accountId != null && iban == null;
        boolean externalShape = type == ReceiverType.EXTERNAL && iban != null && !iban.isBlank() && accountId == null;
        if (!internalShape && !externalShape) {
            throw new IllegalArgumentException("receiver must be either an internal account or an external IBAN");
        }
    }

    public static Receiver internal(UUID accountId) {
        return new Receiver(ReceiverType.INTERNAL, accountId, null);
    }

    public static Receiver external(String iban) {
        return new Receiver(ReceiverType.EXTERNAL, null, iban);
    }

    public boolean isInternal() {
        return type == ReceiverType.INTERNAL;
    }
}
