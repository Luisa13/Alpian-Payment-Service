package ch.example.template.shared.domain;

/**
 * Thrown when a requested aggregate root cannot be found. Mapped to HTTP 404 by {@code
 * GlobalExceptionHandler}.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String entityType, Object id) {
        super(entityType + " not found: " + id);
    }
}
