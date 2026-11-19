package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentRepository;
import org.springframework.stereotype.Component;

/** Task: insert a new payment. Throws {@code DuplicateIdempotencyKeyException} if the key is taken. */
@Component
public class SavePaymentTask implements Task<Payment, Payment> {

    private final PaymentRepository payments;

    public SavePaymentTask(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    public Payment execute(Payment payment) {
        payments.insert(payment);
        return payment;
    }
}
