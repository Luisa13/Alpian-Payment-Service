package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.alpian.payment.application.flows.ReserveFundsFlow;
import ch.alpian.payment.application.tasks.CheckIdempotencyTask;
import ch.alpian.payment.application.tasks.HoldFundsTask;
import ch.alpian.payment.application.tasks.LockSenderAccountTask;
import ch.alpian.payment.application.tasks.SavePaymentTask;
import ch.alpian.payment.application.tasks.ValidateReceiverTask;
import ch.alpian.payment.application.tasks.WriteOutboxTask;
import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountType;
import ch.alpian.payment.domain.FailureReason;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentStatus;
import ch.alpian.payment.domain.Receiver;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReserveFundsFlowTest {

    private final CheckIdempotencyTask checkIdempotency = mock(CheckIdempotencyTask.class);
    private final LockSenderAccountTask lockSender = mock(LockSenderAccountTask.class);
    private final ValidateReceiverTask validateReceiver = mock(ValidateReceiverTask.class);
    private final HoldFundsTask holdFunds = mock(HoldFundsTask.class);
    private final SavePaymentTask savePayment = mock(SavePaymentTask.class);
    private final WriteOutboxTask writeOutbox = mock(WriteOutboxTask.class);

    private final ReserveFundsFlow flow = new ReserveFundsFlow(
            checkIdempotency, lockSender, validateReceiver, holdFunds, savePayment, writeOutbox);

    private static final UUID SENDER_ID = UUID.randomUUID();
    private static final Receiver RECEIVER = Receiver.internal(UUID.randomUUID());

    @Test
    void insufficient_funds_creates_a_rejected_payment() {
        Account sender = new Account(
                SENDER_ID, "Alice", AccountType.CUSTOMER, Money.of("10.00", "CHF"), Money.of("10.00", "CHF"), 0);
        when(checkIdempotency.execute(any())).thenReturn(Optional.empty());
        when(lockSender.execute(any())).thenReturn(sender);

        var args = new ReserveFundsFlow.Arguments("key-1", "h".repeat(64), SENDER_ID, RECEIVER, Money.of("100.00", "CHF"));
        var result = flow.reserve(args);

        assertThat(result).isInstanceOf(ReserveFundsFlow.Result.Rejected.class);
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(result.payment().getFailureReason()).isEqualTo(FailureReason.INSUFFICIENT_FUNDS);
        verify(savePayment).execute(result.payment());
        verify(writeOutbox).execute(result.payment());
        verify(holdFunds, never()).execute(any());
    }

    @Test
    void exact_balance_payment_is_reserved_and_hold_funds_is_called() {
        // Assumption: no upper limit on payment amount is enforced at the application layer.
        Account sender = new Account(
                SENDER_ID, "Alice", AccountType.CUSTOMER, Money.of("50.00", "CHF"), Money.of("50.00", "CHF"), 0);
        when(checkIdempotency.execute(any())).thenReturn(Optional.empty());
        when(lockSender.execute(any())).thenReturn(sender);

        var args = new ReserveFundsFlow.Arguments("key-2", "h".repeat(64), SENDER_ID, RECEIVER, Money.of("50.00", "CHF"));
        var result = flow.reserve(args);

        assertThat(result).isInstanceOf(ReserveFundsFlow.Result.Reserved.class);
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.RESERVED);
        // HoldFundsTask is called — domain arithmetic (available balance → 0) is covered by AccountTest.
        verify(holdFunds).execute(any());
    }

    @Test
    void replay_of_a_rejected_payment_returns_the_original_without_holding_funds() {
        Payment existing = Payment.rejected(
                "key-3", "h".repeat(64), SENDER_ID, RECEIVER, Money.of("10.00", "CHF"), FailureReason.INSUFFICIENT_FUNDS);
        when(checkIdempotency.execute(any())).thenReturn(Optional.of(existing));

        var args = new ReserveFundsFlow.Arguments("key-3", "h".repeat(64), SENDER_ID, RECEIVER, Money.of("10.00", "CHF"));
        var result = flow.reserve(args);

        assertThat(result).isInstanceOf(ReserveFundsFlow.Result.Replayed.class);
        assertThat(result.payment()).isEqualTo(existing);
        verify(lockSender, never()).execute(any());
        verify(holdFunds, never()).execute(any());
        verify(savePayment, never()).execute(any());
    }
}
