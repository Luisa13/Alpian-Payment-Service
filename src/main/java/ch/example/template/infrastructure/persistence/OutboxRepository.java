package ch.example.template.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface OutboxRepository extends CrudRepository<OutboxEntry, UUID> {}
