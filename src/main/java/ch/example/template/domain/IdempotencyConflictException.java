package ch.example.template.domain;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key already used with a different request body: " + idempotencyKey);
    }
}
