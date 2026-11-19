package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentRepository;
import ch.alpian.payment.domain.PaymentStatus;
import org.springframework.stereotype.Component;

/**
 * Task: apply a payment's new status as a guarded update. Returns {@code false} when someone else
 * already moved the payment, in which case the caller must reload it and do nothing.
 */
@Component
public class TransitionPaymentTask implements Task<TransitionPaymentTask.Arguments, Boolean> {

    private final PaymentRepository payments;

    public TransitionPaymentTask(PaymentRepository payments) {
        this.payments = payments;
    }

    public record Arguments(Payment payment, PaymentStatus from) {}

    @Override
    public Boolean execute(Arguments args) {
        return payments.applyTransition(args.payment(), args.from());
    }
}
