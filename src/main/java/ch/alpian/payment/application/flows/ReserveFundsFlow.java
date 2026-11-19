package ch.alpian.payment.application.flows;

import ch.alpian.payment.application.tasks.CheckIdempotencyTask;
import ch.alpian.payment.application.tasks.HoldFundsTask;
import ch.alpian.payment.application.tasks.LockSenderAccountTask;
import ch.alpian.payment.application.tasks.SavePaymentTask;
import ch.alpian.payment.application.tasks.ValidateReceiverTask;
import ch.alpian.payment.application.tasks.WriteOutboxTask;
import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.FailureReason;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.Receiver;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flow (tx1): check a payment and hold its funds in one short transaction.
 *
 * <ol>
 *   <li>idempotency: an existing key with the same hash is replayed, a different hash is a conflict
 *   <li>lock the sender row, check it exists and the currency matches
 *   <li>available balance too low: insert {@code REJECTED} + outbox event; otherwise hold the
 *       funds, insert {@code RESERVED} + outbox event
 * </ol>
 *
 * <p>A unique violation on the idempotency key aborts this transaction and surfaces as {@code
 * DuplicateIdempotencyKeyException}; the caller loads the winner in a new transaction.
 */
@Service
public class ReserveFundsFlow {

    public record Arguments(
            String idempotencyKey, String requestHash, UUID senderAccountId, Receiver receiver, Money amount) {}

    public sealed interface Result {
        Payment payment();

        /** Funds are held; settlement can start. */
        record Reserved(Payment payment) implements Result {}

        /** Refused at intake; nothing is held. */
        record Rejected(Payment payment) implements Result {}

        /** The idempotency key was already used by an identical request. */
        record Replayed(Payment payment) implements Result {}
    }

    private final CheckIdempotencyTask checkIdempotency;
    private final LockSenderAccountTask lockSender;
    private final ValidateReceiverTask validateReceiver;
    private final HoldFundsTask holdFunds;
    private final SavePaymentTask savePayment;
    private final WriteOutboxTask writeOutbox;

    public ReserveFundsFlow(
            CheckIdempotencyTask checkIdempotency,
            LockSenderAccountTask lockSender,
            ValidateReceiverTask validateReceiver,
            HoldFundsTask holdFunds,
            SavePaymentTask savePayment,
            WriteOutboxTask writeOutbox) {
        this.checkIdempotency = checkIdempotency;
        this.lockSender = lockSender;
        this.validateReceiver = validateReceiver;
        this.holdFunds = holdFunds;
        this.savePayment = savePayment;
        this.writeOutbox = writeOutbox;
    }

    @Transactional
    public Result reserve(Arguments args) {
        Optional<Payment> existing =
                checkIdempotency.execute(new CheckIdempotencyTask.Arguments(args.idempotencyKey(), args.requestHash()));
        if (existing.isPresent()) {
            return new Result.Replayed(existing.get());
        }

        Account sender =
                lockSender.execute(new LockSenderAccountTask.Arguments(args.senderAccountId(), args.amount().currency()));
        validateReceiver.execute(new ValidateReceiverTask.Arguments(
                args.receiver(), args.amount().currency()));

        if (!sender.canHold(args.amount())) {
            Payment rejected = Payment.rejected(
                    args.idempotencyKey(),
                    args.requestHash(),
                    args.senderAccountId(),
                    args.receiver(),
                    args.amount(),
                    FailureReason.INSUFFICIENT_FUNDS);
            savePayment.execute(rejected);
            writeOutbox.execute(rejected);
            return new Result.Rejected(rejected);
        }

        holdFunds.execute(new HoldFundsTask.Arguments(sender, args.amount()));
        Payment reserved = Payment.reserved(
                args.idempotencyKey(), args.requestHash(), args.senderAccountId(), args.receiver(), args.amount());
        savePayment.execute(reserved);
        writeOutbox.execute(reserved);
        return new Result.Reserved(reserved);
    }
}
