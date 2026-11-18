package ch.example.template.application.tasks;

import ch.example.template.domain.EntityNotFoundException;
import ch.example.template.domain.Payment;
import ch.example.template.domain.PaymentRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Task: resolve a {@link Payment} by its ID or throw {@link EntityNotFoundException}.
 */
@Component
public class FindPaymentByIdTask implements Task<UUID, Payment> {

    private final PaymentRepository payments;

    public FindPaymentByIdTask(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    public Payment execute(UUID id) {
        return payments.findById(id).orElseThrow(() -> new EntityNotFoundException("Payment", id));
    }
}
