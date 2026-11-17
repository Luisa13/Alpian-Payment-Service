package ch.example.template.transfer.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface OutboxRepository extends CrudRepository<OutboxEntry, UUID> {}
