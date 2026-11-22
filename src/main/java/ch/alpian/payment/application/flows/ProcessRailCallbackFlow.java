package ch.alpian.payment.application.flows;

import ch.alpian.payment.application.tasks.FindPaymentByIdTask;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentStatus;
import ch.alpian.payment.domain.RailCallbackRepository;
import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.exception.CallbackConflictException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flow: processes an asynchronous settlement callback from an external rail.
 *
 * <ul>
 *   <li>Unknown payment → {@code EntityNotFoundException} (404).
 *   <li>Already final with the same outcome (or duplicate {@code eventId}) → {@code REPLAYED} (200).
 *   <li>Already final with a different outcome → {@code CallbackConflictException} (409) + alert metric.
 *   <li>Otherwise delegates to {@link FinalizeSettlementFlow} and returns {@code APPLIED} (204).
 * </ul>
 */
@Service
public class ProcessRailCallbackFlow {

    private static final Logger log = LoggerFactory.getLogger(ProcessRailCallbackFlow.class);

    public enum Result {
        APPLIED,
        REPLAYED
    }

    private final FindPaymentByIdTask findPayment;
    private final FinalizeSettlementFlow finalizeSettlement;
    private final RailCallbackRepository callbacks;
    private final Counter conflictCounter;

    public ProcessRailCallbackFlow(
            FindPaymentByIdTask findPayment,
            FinalizeSettlementFlow finalizeSettlement,
            RailCallbackRepository callbacks,
            MeterRegistry meters) {
        this.findPayment = findPayment;
        this.finalizeSettlement = finalizeSettlement;
        this.callbacks = callbacks;
        this.conflictCounter = Counter.builder("payments.callback.conflict")
                .description("Rail callbacks whose outcome conflicts with an already finalized payment")
                .register(meters);
    }

    @Transactional
    public Result process(UUID eventId, UUID paymentId, SettlementOutcome outcome) {
        Payment before = findPayment.execute(paymentId);
        if (before.getStatus().isFinal()) {
            return handleAlreadyFinal(before, outcome);
        }

        if (!callbacks.record(eventId, paymentId, outcome.status().name())) {
            return Result.REPLAYED;
        }

        Payment after = finalizeSettlement.finalizeSettlement(paymentId, outcome);
        if (!matches(after.getStatus(), outcome.status())) {
            return handleAlreadyFinal(after, outcome);
        }
        return Result.APPLIED;
    }

    private Result handleAlreadyFinal(Payment payment, SettlementOutcome outcome) {
        if (matches(payment.getStatus(), outcome.status())) {
            return Result.REPLAYED;
        }
        conflictCounter.increment();
        log.error(
                "Callback conflict for payment {}: already {} but callback reported {}",
                payment.getId(),
                payment.getStatus(),
                outcome.status());
        throw new CallbackConflictException(
                payment.getId(), payment.getStatus(), outcome.status().name());
    }

    private static boolean matches(PaymentStatus status, SettlementOutcome.Status outcome) {
        return (status == PaymentStatus.COMPLETED && outcome == SettlementOutcome.Status.SETTLED)
                || (status == PaymentStatus.FAILED && outcome == SettlementOutcome.Status.REJECTED);
    }
}
