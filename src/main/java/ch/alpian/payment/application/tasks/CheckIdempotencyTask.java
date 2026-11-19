package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.IdempotencyConflictException;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Task: find the payment already created for an idempotency key.
 *
 * <p>Same key and same request hash returns the stored payment (a replay); same key with a
 * different hash throws {@link IdempotencyConflictException}; an unused key returns empty.
 */
@Component
public class CheckIdempotencyTask implements Task<CheckIdempotencyTask.Arguments, Optional<Payment>> {

    private final PaymentRepository payments;

    public CheckIdempotencyTask(PaymentRepository payments) {
        this.payments = payments;
    }

    public record Arguments(String idempotencyKey, String requestHash) {}

    @Override
    public Optional<Payment> execute(Arguments args) {
        return payments.findByIdempotencyKey(args.idempotencyKey()).map(existing -> {
            if (!existing.getRequestHash().equals(args.requestHash())) {
                throw new IdempotencyConflictException(args.idempotencyKey());
            }
            return existing;
        });
    }
}
