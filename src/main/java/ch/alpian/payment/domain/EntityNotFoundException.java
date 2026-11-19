package ch.alpian.payment.domain;

public class EntityNotFoundException extends RuntimeException {

    private final String entityType;

    public EntityNotFoundException(String entityType, Object id) {
        super(entityType + " not found: " + id);
        this.entityType = entityType;
    }

    public String getEntityType() {
        return entityType;
    }
}
