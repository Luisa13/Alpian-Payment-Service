package ch.alpian.payment.application.flows;

import ch.alpian.payment.application.support.RequestHash;
import ch.alpian.payment.application.support.TransactionRetry;
import ch.alpian.payment.application.tasks.CallSettlementRailTask;
import ch.alpian.payment.application.tasks.CheckIdempotencyTask;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentStatus;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.exception.DuplicateIdempotencyKeyException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Flow (orchestrator): create a payment and settle it inside the request.
 *
 * <p>Runs outside any transaction. Three steps, each its own short unit of work:
 *
 * <ol>
 *   <li>{@link ReserveFundsFlow} (tx1)
 *   <li>{@link CallSettlementRailTask}, with a timeout and no transaction
 *   <li>{@link FinalizeSettlementFlow} (tx2)
 * </ol>
 *
 * Tx1 and tx2 are retried as a whole on deadlock or lock timeout. The two transactions live in
 * separate beans so Spring's proxy is never bypassed by self-invocation.
 */
@Service
public class CreatePaymentFlow {

    private static final Logger log = LoggerFactory.getLogger(CreatePaymentFlow.class);

    public record Arguments(String idempotencyKey, UUID senderAccountId, Receiver receiver, Money amount) {}

    private final ReserveFundsFlow reserveFunds;
    private final SubmitPaymentFlow submitPayment;
    private final FinalizeSettlementFlow finalizeSettlement;
    private final CallSettlementRailTask callRail;
    private final CheckIdempotencyTask checkIdempotency;
    private final TransactionRetry retry;
    private final TransactionTemplate readOnlyTransaction;

    public CreatePaymentFlow(
            ReserveFundsFlow reserveFunds,
            SubmitPaymentFlow submitPayment,
            FinalizeSettlementFlow finalizeSettlement,
            CallSettlementRailTask callRail,
            CheckIdempotencyTask checkIdempotency,
            TransactionRetry retry,
            PlatformTransactionManager transactionManager) {
        this.reserveFunds = reserveFunds;
        this.submitPayment = submitPayment;
        this.finalizeSettlement = finalizeSettlement;
        this.callRail = callRail;
        this.checkIdempotency = checkIdempotency;
        this.retry = retry;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    public PaymentResult initiate(Arguments args) {
        String requestHash = RequestHash.of(args.senderAccountId(), args.receiver(), args.amount());
        var reserveArgs = new ReserveFundsFlow.Arguments(
                args.idempotencyKey(), requestHash, args.senderAccountId(), args.receiver(), args.amount());

        ReserveFundsFlow.Result reservation;
        try {
            reservation = retry.execute(() -> reserveFunds.reserve(reserveArgs));
        } catch (DuplicateIdempotencyKeyException lostRace) {
            return replay(args.idempotencyKey(), requestHash);
        }

        return switch (reservation) {
            case ReserveFundsFlow.Result.Replayed r -> replayed(r.payment());
            case ReserveFundsFlow.Result.Rejected r -> new PaymentResult(r.payment(), PaymentResult.Kind.REJECTED);
            case ReserveFundsFlow.Result.Reserved r -> settle(r.payment());
        };
    }

    private PaymentResult settle(Payment reserved) {
        SettlementOutcome outcome = callRail.execute(reserved);
        if (outcome.status() == SettlementOutcome.Status.ACCEPTED) {
            try {
                Payment submitted = retry.execute(() -> submitPayment.markSubmitted(reserved.getId()));
                return new PaymentResult(submitted, PaymentResult.Kind.ACCEPTED);
            } catch (RuntimeException e) {
                log.error("Could not mark payment {} as SUBMITTED after rail acceptance", reserved.getId(), e);
                return new PaymentResult(reserved, PaymentResult.Kind.ACCEPTED);
            }
        }
        try {
            Payment current = retry.execute(() -> finalizeSettlement.finalizeSettlement(reserved.getId(), outcome));
            return current.getStatus() == PaymentStatus.RESERVED
                    ? new PaymentResult(current, PaymentResult.Kind.ACCEPTED)
                    : new PaymentResult(current, PaymentResult.Kind.CREATED);
        } catch (RuntimeException e) {
            // The funds stay held and the payment stays RESERVED; recovery finishes it with the same
            // payment id, which the rail treats as the same transfer.
            log.error("Could not finalize payment {} after rail outcome {}", reserved.getId(), outcome.status(), e);
            return new PaymentResult(reserved, PaymentResult.Kind.ACCEPTED);
        }
    }

    /** Lost the race on the idempotency key: the winner has committed, load it in a new read-only transaction. */
    private PaymentResult replay(String idempotencyKey, String requestHash) {
        Payment winner = readOnlyTransaction.execute(status -> checkIdempotency
                .execute(new CheckIdempotencyTask.Arguments(idempotencyKey, requestHash))
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency key " + idempotencyKey + " conflicted but no payment was found")));
        return replayed(winner);
    }

    private static PaymentResult replayed(Payment payment) {
        PaymentResult.Kind kind = switch (payment.getStatus()) {
            case REJECTED -> PaymentResult.Kind.REJECTED;
            case RESERVED -> PaymentResult.Kind.ACCEPTED;
            case COMPLETED, FAILED -> PaymentResult.Kind.REPLAYED;
        };
        return new PaymentResult(payment, kind);
    }
}
