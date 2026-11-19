package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.alpian.payment.application.flows.FinalizeSettlementFlow;
import ch.alpian.payment.application.tasks.FindPaymentByIdTask;
import ch.alpian.payment.application.tasks.LockAccountsTask;
import ch.alpian.payment.application.tasks.PostLedgerEntriesTask;
import ch.alpian.payment.application.tasks.RecordSettlementAttemptTask;
import ch.alpian.payment.application.tasks.ResolveReceiverAccountTask;
import ch.alpian.payment.application.tasks.SaveAccountsTask;
import ch.alpian.payment.application.tasks.TransitionPaymentTask;
import ch.alpian.payment.application.tasks.WriteOutboxTask;
import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountType;
import ch.alpian.payment.domain.FailureReason;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentStatus;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.domain.SettlementOutcome;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinalizeSettlementFlowTest {

    private final FindPaymentByIdTask findPayment = mock(FindPaymentByIdTask.class);
    private final TransitionPaymentTask transition = mock(TransitionPaymentTask.class);
    private final ResolveReceiverAccountTask resolveReceiver = mock(ResolveReceiverAccountTask.class);
    private final LockAccountsTask lockAccounts = mock(LockAccountsTask.class);
    private final SaveAccountsTask saveAccounts = mock(SaveAccountsTask.class);
    private final PostLedgerEntriesTask postLedger = mock(PostLedgerEntriesTask.class);
    private final RecordSettlementAttemptTask recordAttempt = mock(RecordSettlementAttemptTask.class);
    private final WriteOutboxTask writeOutbox = mock(WriteOutboxTask.class);

    private final FinalizeSettlementFlow flow = new FinalizeSettlementFlow(
            transition, findPayment, resolveReceiver, lockAccounts, saveAccounts,
            postLedger, recordAttempt, writeOutbox);

    @Test
    void finalize_settlement_returns_already_moved_when_not_reserved_or_transition_loses_race() {
        Payment completed = Payment.reserved(
                "k1",
                "h".repeat(64),
                UUID.randomUUID(),
                Receiver.internal(UUID.randomUUID()),
                Money.of("10.00", "CHF"));
        completed.complete("REF-1");
        when(findPayment.execute(completed.getId())).thenReturn(completed);

        assertThat(flow.finalizeSettlement(completed.getId(), SettlementOutcome.settled("REF-1"))
                        .getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);

        Payment reserved = Payment.reserved(
                "k2",
                "h".repeat(64),
                UUID.randomUUID(),
                Receiver.internal(UUID.randomUUID()),
                Money.of("10.00", "CHF"));
        when(findPayment.execute(reserved.getId())).thenReturn(reserved, completed);
        when(transition.execute(any())).thenReturn(false);

        assertThat(flow.finalizeSettlement(reserved.getId(), SettlementOutcome.rejected())
                        .getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void settled_outcome_completes_payment_posts_ledger_and_writes_outbox() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        Payment payment = Payment.reserved(
                "k3", "h".repeat(64), senderId, Receiver.internal(receiverId), Money.of("25.00", "CHF"));
        // Sender has 25.00 on hold (balance=100, available=75) so settleDebit(25) succeeds.
        Account sender = new Account(
                senderId, "Alice", AccountType.CUSTOMER, Money.of("100.00", "CHF"), Money.of("75.00", "CHF"), 0);
        Account receiver = new Account(
                receiverId, "Bob", AccountType.CUSTOMER, Money.of("0.00", "CHF"), Money.of("0.00", "CHF"), 0);

        when(findPayment.execute(payment.getId())).thenReturn(payment);
        when(transition.execute(any())).thenReturn(true);
        when(resolveReceiver.execute(any())).thenReturn(receiverId);
        when(lockAccounts.execute(any())).thenReturn(Map.of(senderId, sender, receiverId, receiver));

        Payment result = flow.finalizeSettlement(payment.getId(), SettlementOutcome.settled("REF-2"));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(postLedger).execute(any());
        verify(writeOutbox).execute(any());
        verify(recordAttempt, never()).execute(any());
    }

    @Test
    void rejected_outcome_fails_payment_releases_hold_and_writes_outbox_without_ledger_entries() {
        UUID senderId = UUID.randomUUID();
        Payment payment = Payment.reserved(
                "k4", "h".repeat(64), senderId, Receiver.internal(UUID.randomUUID()), Money.of("25.00", "CHF"));
        // Sender has 25.00 on hold so release(25) succeeds.
        Account sender = new Account(
                senderId, "Alice", AccountType.CUSTOMER, Money.of("100.00", "CHF"), Money.of("75.00", "CHF"), 0);

        when(findPayment.execute(payment.getId())).thenReturn(payment);
        when(transition.execute(any())).thenReturn(true);
        when(lockAccounts.execute(any())).thenReturn(Map.of(senderId, sender));

        Payment result = flow.finalizeSettlement(payment.getId(), SettlementOutcome.rejected());

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo(FailureReason.RAIL_REJECTED);
        verify(writeOutbox).execute(any());
        verify(postLedger, never()).execute(any());
        verify(resolveReceiver, never()).execute(any());
        verify(recordAttempt, never()).execute(any());
    }

    @Test
    void unknown_outcome_records_the_attempt_and_leaves_payment_reserved() {
        UUID senderId = UUID.randomUUID();
        Payment payment = Payment.reserved(
                "k5", "h".repeat(64), senderId, Receiver.internal(UUID.randomUUID()), Money.of("25.00", "CHF"));

        when(findPayment.execute(payment.getId())).thenReturn(payment);

        Payment result = flow.finalizeSettlement(payment.getId(), SettlementOutcome.unknown());

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.RESERVED);
        verify(recordAttempt).execute(any());
        verify(transition, never()).execute(any());
        verify(writeOutbox, never()).execute(any());
        verify(postLedger, never()).execute(any());
    }
}
