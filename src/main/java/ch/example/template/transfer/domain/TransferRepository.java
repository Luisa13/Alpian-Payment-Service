package ch.example.template.transfer.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

/**
 * Domain port for Transfer persistence. Spring Data JDBC provides the implementation; the
 * infrastructure adapter ({@code TransferRepositoryImpl}) is not needed — Spring creates it
 * automatically.
 *
 * <p>Keeping this interface in the domain package is a pragmatic compromise: it imports from
 * {@code org.springframework.data.repository}, which is a thin abstraction with no runtime
 * framework coupling beyond the annotation processor.
 */
public interface TransferRepository extends CrudRepository<Transfer, UUID> {

    Optional<Transfer> findById(UUID id);
}
