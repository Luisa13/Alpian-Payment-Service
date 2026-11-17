package ch.example.template.shared.application;

/**
 * Thrown when an idempotency key is reused with a different request body.
 *
 * <p>Same key + same body → return the stored response (no exception). Same key + different body →
 * this exception → HTTP 409.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key already used with a different request body: " + idempotencyKey);
    }
}
