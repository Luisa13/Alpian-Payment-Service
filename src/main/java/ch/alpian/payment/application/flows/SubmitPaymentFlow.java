package ch.alpian.payment.application.flows;

import ch.alpian.payment.application.tasks.FindPaymentByIdTask;
import ch.alpian.payment.application.tasks.TransitionPaymentTask;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flow: guarded transition {@code RESERVED → SUBMITTED} after an async rail accepts the submission. */
@Service
public class SubmitPaymentFlow {

    private final FindPaymentByIdTask findPayment;
    private final TransitionPaymentTask transitionPayment;

    public SubmitPaymentFlow(FindPaymentByIdTask findPayment, TransitionPaymentTask transitionPayment) {
        this.findPayment = findPayment;
        this.transitionPayment = transitionPayment;
    }

    @Transactional
    public Payment markSubmitted(UUID paymentId) {
        Payment payment = findPayment.execute(paymentId);
        if (payment.getStatus() != PaymentStatus.RESERVED) {
            return payment;
        }
        payment.submit();
        if (!transitionPayment.execute(new TransitionPaymentTask.Arguments(payment, PaymentStatus.RESERVED))) {
            return findPayment.execute(paymentId);
        }
        return payment;
    }
}
