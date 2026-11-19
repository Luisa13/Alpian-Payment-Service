package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.alpian.payment.application.flows.CreatePaymentFlow;
import ch.alpian.payment.application.flows.FinalizeSettlementFlow;
import ch.alpian.payment.application.flows.GetAccountFlow;
import ch.alpian.payment.application.flows.GetPaymentFlow;
import ch.alpian.payment.application.flows.PaymentResult;
import ch.alpian.payment.application.flows.ReserveFundsFlow;
import ch.alpian.payment.application.support.TransactionRetry;
import ch.alpian.payment.application.tasks.CallSettlementRailTask;
import ch.alpian.payment.application.tasks.CheckIdempotencyTask;
import ch.alpian.payment.application.tasks.FindAccountByIdTask;
import ch.alpian.payment.application.tasks.FindPaymentByIdTask;
import ch.alpian.payment.application.tasks.LockAccountsTask;
import ch.alpian.payment.application.tasks.LockSenderAccountTask;
import ch.alpian.payment.application.tasks.PostLedgerEntriesTask;
import ch.alpian.payment.application.tasks.RecordSettlementAttemptTask;
import ch.alpian.payment.application.tasks.ResolveReceiverAccountTask;
import ch.alpian.payment.application.tasks.SaveAccountsTask;
import ch.alpian.payment.application.tasks.TransitionPaymentTask;
import ch.alpian.payment.application.tasks.ValidateReceiverTask;
import ch.alpian.payment.application.tasks.WriteOutboxTask;
import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.AccountType;
import ch.alpian.payment.domain.CurrencyMismatchException;
import ch.alpian.payment.domain.EntityNotFoundException;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentStatus;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.SettlementRail;
import ch.alpian.payment.domain.UnknownAccountException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class ApplicationTasksAndFlowsTest {

    @Test
    void lock_accounts_throws_when_any_account_is_missing() {
        AccountRepository repo = mock(AccountRepository.class);
        UUID presentId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        Account present = new Account(
                presentId, "Alice", AccountType.CUSTOMER, Money.of("10.00", "CHF"), Money.of("10.00", "CHF"), 0);
        when(repo.findAllByIdForUpdate(any())).thenReturn(List.of(present));

        var task = new LockAccountsTask(repo);
        assertThatThrownBy(() -> task.execute(List.of(presentId, missingId)))
                .isInstanceOf(UnknownAccountException.class);
    }

    @Test
    void validate_receiver_throws_on_unknown_or_currency_mismatch() {
        AccountRepository repo = mock(AccountRepository.class);
        UUID missing = UUID.randomUUID();
        UUID eurId = UUID.randomUUID();
        Account eurAccount =
                new Account(eurId, "Bob", AccountType.CUSTOMER, Money.of("0.00", "EUR"), Money.of("0.00", "EUR"), 0);
        when(repo.findById(missing)).thenReturn(Optional.empty());
        when(repo.findById(eurId)).thenReturn(Optional.of(eurAccount));

        var task = new ValidateReceiverTask(repo);
        assertThatThrownBy(() -> task.execute(new ValidateReceiverTask.Arguments(Receiver.internal(missing), "CHF")))
                .isInstanceOf(UnknownAccountException.class);
        assertThatThrownBy(() -> task.execute(new ValidateReceiverTask.Arguments(Receiver.internal(eurId), "CHF")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void lock_sender_throws_on_unknown_or_currency_mismatch() {
        AccountRepository repo = mock(AccountRepository.class);
        UUID missing = UUID.randomUUID();
        UUID eurId = UUID.randomUUID();
        Account eurAccount = new Account(
                eurId, "Alice", AccountType.CUSTOMER, Money.of("10.00", "EUR"), Money.of("10.00", "EUR"), 0);
        when(repo.findByIdForUpdate(missing)).thenReturn(Optional.empty());
        when(repo.findByIdForUpdate(eurId)).thenReturn(Optional.of(eurAccount));

        var task = new LockSenderAccountTask(repo);
        assertThatThrownBy(() -> task.execute(new LockSenderAccountTask.Arguments(missing, "CHF")))
                .isInstanceOf(UnknownAccountException.class);
        assertThatThrownBy(() -> task.execute(new LockSenderAccountTask.Arguments(eurId, "CHF")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void call_settlement_rail_returns_unknown_on_timeout_exception_or_interruption() {
        SettlementRail rail = mock(SettlementRail.class);
        when(rail.settle(any())).thenThrow(new IllegalStateException("rail exploded"));
        Payment payment = Payment.reserved(
                "k",
                "h".repeat(64),
                UUID.randomUUID(),
                Receiver.external("CH9300762011623852957"),
                Money.of("10.00", "CHF"));

        var task = new CallSettlementRailTask(rail, Duration.ofMillis(100));
        assertThat(task.execute(payment)).isEqualTo(SettlementOutcome.unknown());

        doAnswer(inv -> {
                    Thread.sleep(400);
                    return SettlementOutcome.settled("LATE");
                })
                .when(rail)
                .settle(any());
        assertThat(task.execute(payment)).isEqualTo(SettlementOutcome.unknown());

        Thread.currentThread().interrupt();
        try {
            assertThat(task.execute(payment)).isEqualTo(SettlementOutcome.unknown());
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void finalize_settlement_returns_already_moved_when_not_reserved_or_transition_loses_race() {
        FindPaymentByIdTask findPayment = mock(FindPaymentByIdTask.class);
        TransitionPaymentTask transition = mock(TransitionPaymentTask.class);
        var flow = new FinalizeSettlementFlow(
                transition,
                findPayment,
                mock(ResolveReceiverAccountTask.class),
                mock(LockAccountsTask.class),
                mock(SaveAccountsTask.class),
                mock(PostLedgerEntriesTask.class),
                mock(RecordSettlementAttemptTask.class),
                mock(WriteOutboxTask.class));

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
    void get_account_and_get_payment_flows_delegate_and_throw_not_found() {
        AccountRepository accounts = mock(AccountRepository.class);
        UUID accId = UUID.randomUUID();
        Account account = new Account(
                accId, "Alice", AccountType.CUSTOMER, Money.of("50.00", "CHF"), Money.of("50.00", "CHF"), 0);
        when(accounts.findById(accId)).thenReturn(Optional.of(account));
        var findAccount = new FindAccountByIdTask(accounts);
        var getAccountFlow = new GetAccountFlow(findAccount);
        assertThat(getAccountFlow.initiate(new GetAccountFlow.Arguments(accId))).isEqualTo(account);
        UUID missingAcc = UUID.randomUUID();
        assertThatThrownBy(() -> getAccountFlow.initiate(new GetAccountFlow.Arguments(missingAcc)))
                .isInstanceOf(EntityNotFoundException.class);

        FindPaymentByIdTask findPayment = mock(FindPaymentByIdTask.class);
        Payment payment = Payment.reserved(
                "k", "h".repeat(64), accId, Receiver.internal(UUID.randomUUID()), Money.of("10.00", "CHF"));
        when(findPayment.execute(payment.getId())).thenReturn(payment);
        var getPaymentFlow = new GetPaymentFlow(findPayment);
        assertThat(getPaymentFlow.initiate(new GetPaymentFlow.Arguments(payment.getId())))
                .isEqualTo(payment);
    }

    @Test
    void create_payment_flow_returns_accepted_on_unknown_outcome_or_finalization_failure() {
        ReserveFundsFlow reserve = mock(ReserveFundsFlow.class);
        FinalizeSettlementFlow finalize = mock(FinalizeSettlementFlow.class);
        CallSettlementRailTask callRail = mock(CallSettlementRailTask.class);
        CheckIdempotencyTask checkIdem = mock(CheckIdempotencyTask.class);
        var flow = new CreatePaymentFlow(
                reserve,
                finalize,
                callRail,
                checkIdem,
                new TransactionRetry(1, 1),
                mock(PlatformTransactionManager.class));

        UUID sender = UUID.randomUUID();
        Receiver receiver = Receiver.internal(UUID.randomUUID());
        Money amount = Money.of("10.00", "CHF");
        Payment reserved = Payment.reserved("k-acc", "h".repeat(64), sender, receiver, amount);

        when(reserve.reserve(any())).thenReturn(new ReserveFundsFlow.Result.Reserved(reserved));
        when(callRail.execute(reserved)).thenReturn(SettlementOutcome.unknown());
        when(finalize.finalizeSettlement(reserved.getId(), SettlementOutcome.unknown()))
                .thenReturn(reserved);

        PaymentResult accepted = flow.initiate(new CreatePaymentFlow.Arguments("k-acc", sender, receiver, amount));
        assertThat(accepted.kind()).isEqualTo(PaymentResult.Kind.ACCEPTED);

        when(finalize.finalizeSettlement(reserved.getId(), SettlementOutcome.unknown()))
                .thenThrow(new RuntimeException("db glitch"));
        PaymentResult fallbackAccepted =
                flow.initiate(new CreatePaymentFlow.Arguments("k-acc", sender, receiver, amount));
        assertThat(fallbackAccepted.kind()).isEqualTo(PaymentResult.Kind.ACCEPTED);
    }
}
