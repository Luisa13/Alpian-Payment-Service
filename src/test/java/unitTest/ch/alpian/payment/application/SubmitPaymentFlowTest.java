package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.alpian.payment.application.flows.SubmitPaymentFlow;
import ch.alpian.payment.application.tasks.FindPaymentByIdTask;
import ch.alpian.payment.application.tasks.TransitionPaymentTask;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentStatus;
import ch.alpian.payment.domain.Receiver;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubmitPaymentFlowTest {

    private final FindPaymentByIdTask findPayment = mock(FindPaymentByIdTask.class);
    private final TransitionPaymentTask transitionPayment = mock(TransitionPaymentTask.class);
    private final SubmitPaymentFlow flow = new SubmitPaymentFlow(findPayment, transitionPayment);

    @Test
    void markSubmitted_transitions_reserved_to_submitted() {
        Payment reserved = Payment.reserved(
                "k1",
                "h".repeat(64),
                UUID.randomUUID(),
                Receiver.external("CH9300762011623852957"),
                Money.of("10.00", "CHF"));
        when(findPayment.execute(reserved.getId())).thenReturn(reserved);
        when(transitionPayment.execute(any())).thenReturn(true);

        Payment result = flow.markSubmitted(reserved.getId());

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUBMITTED);
        assertThat(result.getSubmittedAt()).isNotNull();
    }

    @Test
    void markSubmitted_returns_latest_when_already_moved_or_race_lost() {
        Payment completed = Payment.reserved(
                "k2",
                "h".repeat(64),
                UUID.randomUUID(),
                Receiver.external("CH9300762011623852957"),
                Money.of("10.00", "CHF"));
        completed.complete("REF-1");
        when(findPayment.execute(completed.getId())).thenReturn(completed);

        assertThat(flow.markSubmitted(completed.getId()).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(transitionPayment, never()).execute(any());

        Payment reserved = Payment.reserved(
                "k3",
                "h".repeat(64),
                UUID.randomUUID(),
                Receiver.external("CH9300762011623852957"),
                Money.of("10.00", "CHF"));
        when(findPayment.execute(reserved.getId())).thenReturn(reserved, completed);
        when(transitionPayment.execute(any())).thenReturn(false);

        assertThat(flow.markSubmitted(reserved.getId()).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }
}
