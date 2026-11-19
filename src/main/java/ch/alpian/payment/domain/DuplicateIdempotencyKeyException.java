package ch.alpian.payment.domain;

/** Another payment already uses this idempotency key (unique constraint violation). */
public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(String idempotencyKey, Throwable cause) {
        super("Idempotency key already used: " + idempotencyKey, cause);
    }
}
