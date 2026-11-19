package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentRepository;
import org.springframework.stereotype.Component;

/** Task: count one settlement attempt that ended UNKNOWN. Recovery takes over from there. */
@Component
public class RecordSettlementAttemptTask implements Task<Payment, Void> {

    private final PaymentRepository payments;

    public RecordSettlementAttemptTask(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    public Void execute(Payment payment) {
        payments.recordSettlementAttempt(payment.getId());
        return null;
    }
}
