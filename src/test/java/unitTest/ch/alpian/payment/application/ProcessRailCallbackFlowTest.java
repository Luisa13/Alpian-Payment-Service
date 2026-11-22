package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.alpian.payment.application.flows.FinalizeSettlementFlow;
import ch.alpian.payment.application.flows.ProcessRailCallbackFlow;
import ch.alpian.payment.application.tasks.FindPaymentByIdTask;
import ch.alpian.payment.domain.FailureReason;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.RailCallbackRepository;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.exception.CallbackConflictException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessRailCallbackFlowTest {

    private final FindPaymentByIdTask findPayment = mock(FindPaymentByIdTask.class);
    private final FinalizeSettlementFlow finalizeSettlement = mock(FinalizeSettlementFlow.class);
    private final RailCallbackRepository callbacks = mock(RailCallbackRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ProcessRailCallbackFlow flow =
            new ProcessRailCallbackFlow(findPayment, finalizeSettlement, callbacks, meters);

    private static Payment newPayment() {
        return Payment.reserved(
                "k-" + UUID.randomUUID(),
                "h".repeat(64),
                UUID.randomUUID(),
                Receiver.external("CH9300762011623852957"),
                Money.of("25.00", "CHF"));
    }

    @Test
    void process_applies_new_callback_and_returns_applied() {
        Payment submitted = newPayment();
        submitted.submit();
        Payment completed = newPayment();
        completed.submit();
        completed.complete("SIM-1");
        UUID eventId = UUID.randomUUID();

        when(findPayment.execute(submitted.getId())).thenReturn(submitted);
        when(callbacks.record(eventId, submitted.getId(), "SETTLED")).thenReturn(true);
        when(finalizeSettlement.finalizeSettlement(submitted.getId(), SettlementOutcome.settled("SIM-1")))
                .thenReturn(completed);

        ProcessRailCallbackFlow.Result result =
                flow.process(eventId, submitted.getId(), SettlementOutcome.settled("SIM-1"));

        assertThat(result).isEqualTo(ProcessRailCallbackFlow.Result.APPLIED);
    }

    @Test
    void process_returns_replayed_when_already_final_with_same_outcome_or_duplicate_event() {
        Payment completed = newPayment();
        completed.complete("SIM-1");
        when(findPayment.execute(completed.getId())).thenReturn(completed);

        assertThat(flow.process(UUID.randomUUID(), completed.getId(), SettlementOutcome.settled("SIM-1")))
                .isEqualTo(ProcessRailCallbackFlow.Result.REPLAYED);
        verify(finalizeSettlement, never()).finalizeSettlement(any(), any());

        Payment submitted = newPayment();
        submitted.submit();
        UUID dupEvent = UUID.randomUUID();
        when(findPayment.execute(submitted.getId())).thenReturn(submitted);
        when(callbacks.record(dupEvent, submitted.getId(), "REJECTED")).thenReturn(false);

        assertThat(flow.process(dupEvent, submitted.getId(), SettlementOutcome.rejected()))
                .isEqualTo(ProcessRailCallbackFlow.Result.REPLAYED);
        verify(finalizeSettlement, never()).finalizeSettlement(any(), any());
    }

    @Test
    void process_throws_conflict_and_increments_metric_when_final_with_different_outcome() {
        Payment failed = newPayment();
        failed.fail(FailureReason.RAIL_REJECTED);
        when(findPayment.execute(failed.getId())).thenReturn(failed);

        assertThatThrownBy(() -> flow.process(UUID.randomUUID(), failed.getId(), SettlementOutcome.settled("SIM-1")))
                .isInstanceOf(CallbackConflictException.class);
        assertThat(meters.counter("payments.callback.conflict").count()).isEqualTo(1.0);
    }
}
