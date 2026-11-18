package ch.example.template.application.tasks;

import ch.example.template.domain.Money;
import ch.example.template.domain.Payment;
import ch.example.template.domain.PaymentRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Task: instantiate and persist a new {@link Payment} aggregate.
 */
@Component
public class SavePaymentTask implements Task<SavePaymentTask.Arguments, Payment> {

    private final PaymentRepository payments;

    public SavePaymentTask(PaymentRepository payments) {
        this.payments = payments;
    }

    public record Arguments(Money amount, UUID sourceAccountId, UUID targetAccountId, String description) {}

    @Override
    public Payment execute(Arguments args) {
        Payment payment =
                Payment.create(args.amount(), args.sourceAccountId(), args.targetAccountId(), args.description());
        return payments.save(payment);
    }
}
