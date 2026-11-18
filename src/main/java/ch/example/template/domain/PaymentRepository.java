package ch.example.template.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface PaymentRepository extends CrudRepository<Payment, UUID> {

    Optional<Payment> findById(UUID id);
}
