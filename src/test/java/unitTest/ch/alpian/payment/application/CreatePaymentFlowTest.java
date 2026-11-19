package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.alpian.payment.application.flows.CreatePaymentFlow;
import ch.alpian.payment.application.flows.FinalizeSettlementFlow;
import ch.alpian.payment.application.flows.PaymentResult;
import ch.alpian.payment.application.flows.ReserveFundsFlow;
import ch.alpian.payment.application.support.TransactionRetry;
import ch.alpian.payment.application.tasks.CallSettlementRailTask;
import ch.alpian.payment.application.tasks.CheckIdempotencyTask;
import ch.alpian.payment.domain.DuplicateIdempotencyKeyException;
import ch.alpian.payment.domain.FailureReason;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.domain.SettlementOutcome;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * Unit tests for CreatePaymentFlow routing logic.
 *
 * <p>CreatePaymentFlow takes PlatformTransactionManager as a constructor argument (used only in
 * the DuplicateIdempotencyKeyException path). PlatformTransactionManager and TransactionRetry are
 * mocked here so the tests run without Spring context or database.
 */
class CreatePaymentFlowTest {

    private final ReserveFundsFlow reserveFunds = mock(ReserveFundsFlow.class);
    private final FinalizeSettlementFlow finalizeSettlement = mock(FinalizeSettlementFlow.class);
    private final CallSettlementRailTask callRail = mock(CallSettlementRailTask.class);
    private final CheckIdempotencyTask checkIdempotency = mock(CheckIdempotencyTask.class);
    private final TransactionRetry retry = mock(TransactionRetry.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

    private final CreatePaymentFlow flow =
            new CreatePaymentFlow(reserveFunds, finalizeSettlement, callRail, checkIdempotency, retry, txManager);

    private static final UUID SENDER_ID = UUID.randomUUID();
    private static final Receiver RECEIVER = Receiver.internal(UUID.randomUUID());
    private static final Money AMOUNT = Money.of("50.00", "CHF");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // doAnswer avoids invoking the existing stub during re-stubbing in the duplicate key test.
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get()).when(retry).execute(any());
        // Make TransactionTemplate.execute() invoke its callback (used only in the
        // DuplicateIdempotencyKeyException path).
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    private static CreatePaymentFlow.Arguments args() {
        return new CreatePaymentFlow.Arguments("key", SENDER_ID, RECEIVER, AMOUNT);
    }

    @Test
    void rejected_reservation_returns_rejected_kind_without_calling_the_rail() {
        Payment rejected =
                Payment.rejected("key", "h".repeat(64), SENDER_ID, RECEIVER, AMOUNT, FailureReason.INSUFFICIENT_FUNDS);
        when(reserveFunds.reserve(any())).thenReturn(new ReserveFundsFlow.Result.Rejected(rejected));

        PaymentResult result = flow.initiate(args());

        assertThat(result.kind()).isEqualTo(PaymentResult.Kind.REJECTED);
        assertThat(result.payment()).isEqualTo(rejected);
        verify(callRail, never()).execute(any());
    }

    @Test
    void replayed_reservation_returns_replayed_kind_without_calling_the_rail() {
        Payment existing = Payment.reserved("key", "h".repeat(64), SENDER_ID, RECEIVER, AMOUNT);
        existing.complete("REF-0");
        when(reserveFunds.reserve(any())).thenReturn(new ReserveFundsFlow.Result.Replayed(existing));

        PaymentResult result = flow.initiate(args());

        assertThat(result.kind()).isEqualTo(PaymentResult.Kind.REPLAYED);
        verify(callRail, never()).execute(any());
    }

    @Test
    void settled_rail_outcome_returns_created_kind() {
        Payment reserved = Payment.reserved("key", "h".repeat(64), SENDER_ID, RECEIVER, AMOUNT);
        Payment finalized = Payment.reserved("key-f", "h".repeat(64), SENDER_ID, RECEIVER, AMOUNT);
        finalized.complete("REF-1");

        when(reserveFunds.reserve(any())).thenReturn(new ReserveFundsFlow.Result.Reserved(reserved));
        when(callRail.execute(any())).thenReturn(SettlementOutcome.settled("REF-1"));
        when(finalizeSettlement.finalizeSettlement(any(), any())).thenReturn(finalized);

        PaymentResult result = flow.initiate(args());

        assertThat(result.kind()).isEqualTo(PaymentResult.Kind.CREATED);
        assertThat(result.payment()).isEqualTo(finalized);
    }

    @Test
    void finalize_failure_returns_accepted_with_the_still_reserved_payment() {
        Payment reserved = Payment.reserved("key", "h".repeat(64), SENDER_ID, RECEIVER, AMOUNT);

        when(reserveFunds.reserve(any())).thenReturn(new ReserveFundsFlow.Result.Reserved(reserved));
        when(callRail.execute(any())).thenReturn(SettlementOutcome.unknown());
        when(finalizeSettlement.finalizeSettlement(any(), any())).thenThrow(new RuntimeException("db blew up"));

        PaymentResult result = flow.initiate(args());

        assertThat(result.kind()).isEqualTo(PaymentResult.Kind.ACCEPTED);
        assertThat(result.payment()).isEqualTo(reserved);
    }

    @Test
    void duplicate_idempotency_key_replays_the_winner_in_a_read_only_transaction() {
        Payment winner = Payment.reserved("key", "h".repeat(64), SENDER_ID, RECEIVER, AMOUNT);
        winner.complete("REF-W");

        doThrow(new DuplicateIdempotencyKeyException("key", null)).when(retry).execute(any());
        when(checkIdempotency.execute(any())).thenReturn(Optional.of(winner));

        PaymentResult result = flow.initiate(args());

        assertThat(result.kind()).isEqualTo(PaymentResult.Kind.REPLAYED);
        assertThat(result.payment()).isEqualTo(winner);
    }
}
