package ch.example.template.domain;

public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String entityType, Object id) {
        super(entityType + " not found: " + id);
    }
}
