package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentRepository;
import ch.alpian.payment.domain.exception.EntityNotFoundException;
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
