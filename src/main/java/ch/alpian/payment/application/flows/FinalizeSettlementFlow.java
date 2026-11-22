package ch.alpian.payment.application.flows;

import ch.alpian.payment.application.tasks.FindPaymentByIdTask;
import ch.alpian.payment.application.tasks.LockAccountsTask;
import ch.alpian.payment.application.tasks.PostLedgerEntriesTask;
import ch.alpian.payment.application.tasks.RecordSettlementAttemptTask;
import ch.alpian.payment.application.tasks.ResolveReceiverAccountTask;
import ch.alpian.payment.application.tasks.SaveAccountsTask;
import ch.alpian.payment.application.tasks.TransitionPaymentTask;
import ch.alpian.payment.application.tasks.WriteOutboxTask;
import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.FailureReason;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentStatus;
import ch.alpian.payment.domain.SettlementOutcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flow (tx2): turn the rail's answer into a final state in one short transaction.
 *
 * <ul>
 *   <li>{@code SETTLED}: guarded {@code RESERVED → COMPLETED}; lock sender and receiver in id order;
 *       debit the sender, credit the receiver (or the clearing account), two ledger entries, outbox
 *   <li>{@code REJECTED}: guarded {@code RESERVED → FAILED}; release the hold; outbox
 *   <li>{@code UNKNOWN}: nothing moves, the attempt is counted; recovery takes over
 * </ul>
 *
 * <p>Every transition is a guarded update. If it updates zero rows, someone else already moved the
 * payment: it is reloaded and nothing else happens.
 */
@Service
public class FinalizeSettlementFlow {

    private static final Logger log = LoggerFactory.getLogger(FinalizeSettlementFlow.class);

    private final TransitionPaymentTask transitionPayment;
    private final FindPaymentByIdTask findPayment;
    private final ResolveReceiverAccountTask resolveReceiver;
    private final LockAccountsTask lockAccounts;
    private final SaveAccountsTask saveAccounts;
    private final PostLedgerEntriesTask postLedger;
    private final RecordSettlementAttemptTask recordAttempt;
    private final WriteOutboxTask writeOutbox;

    public FinalizeSettlementFlow(
            TransitionPaymentTask transitionPayment,
            FindPaymentByIdTask findPayment,
            ResolveReceiverAccountTask resolveReceiver,
            LockAccountsTask lockAccounts,
            SaveAccountsTask saveAccounts,
            PostLedgerEntriesTask postLedger,
            RecordSettlementAttemptTask recordAttempt,
            WriteOutboxTask writeOutbox) {
        this.transitionPayment = transitionPayment;
        this.findPayment = findPayment;
        this.resolveReceiver = resolveReceiver;
        this.lockAccounts = lockAccounts;
        this.saveAccounts = saveAccounts;
        this.postLedger = postLedger;
        this.recordAttempt = recordAttempt;
        this.writeOutbox = writeOutbox;
    }

    /**
     * The payment is reloaded inside the transaction, so a retried transaction never works on a
     * stale in-memory copy.
     *
     * @return the payment as it stands after this call (possibly moved by someone else)
     */
    @Transactional
    public Payment finalizeSettlement(UUID paymentId, SettlementOutcome outcome) {
        Payment payment = findPayment.execute(paymentId);
        if (payment.getStatus() != PaymentStatus.RESERVED && payment.getStatus() != PaymentStatus.SUBMITTED) {
            return alreadyMoved(payment);
        }
        return switch (outcome.status()) {
            case SETTLED -> complete(payment, outcome.reference());
            case REJECTED -> fail(payment);
            case ACCEPTED -> payment;
            case UNKNOWN, PENDING -> {
                recordAttempt.execute(payment);
                yield payment;
            }
        };
    }

    private Payment complete(Payment payment, String railReference) {
        Payment transitioned = tryTransition(payment, p -> p.complete(railReference));
        if (transitioned == null) {
            return alreadyMoved(payment);
        }

        UUID receiverAccountId = resolveReceiver.execute(transitioned);
        Map<UUID, Account> locked = lockAccounts.execute(List.of(transitioned.getSenderAccountId(), receiverAccountId));
        Account sender = locked.get(transitioned.getSenderAccountId());
        Account receiver = locked.get(receiverAccountId);
        sender.settleDebit(transitioned.getAmount());
        receiver.credit(transitioned.getAmount());
        saveAccounts.execute(List.of(sender, receiver));

        postLedger.execute(new PostLedgerEntriesTask.Arguments(transitioned, receiverAccountId));
        writeOutbox.execute(transitioned);
        return transitioned;
    }

    private Payment fail(Payment payment) {
        Payment transitioned = tryTransition(payment, p -> p.fail(FailureReason.RAIL_REJECTED));
        if (transitioned == null) {
            return alreadyMoved(payment);
        }

        Account sender =
                lockAccounts.execute(List.of(transitioned.getSenderAccountId())).get(transitioned.getSenderAccountId());
        sender.release(transitioned.getAmount());
        saveAccounts.execute(List.of(sender));

        writeOutbox.execute(transitioned);
        return transitioned;
    }

    private Payment tryTransition(Payment payment, Consumer<Payment> mutator) {
        PaymentStatus from = payment.getStatus();
        mutator.accept(payment);
        if (transitionPayment.execute(new TransitionPaymentTask.Arguments(payment, from))) {
            return payment;
        }
        if (from == PaymentStatus.RESERVED) {
            Payment reloaded = findPayment.execute(payment.getId());
            if (reloaded.getStatus() == PaymentStatus.SUBMITTED) {
                mutator.accept(reloaded);
                if (transitionPayment.execute(new TransitionPaymentTask.Arguments(reloaded, PaymentStatus.SUBMITTED))) {
                    return reloaded;
                }
            }
        }
        return null;
    }

    private Payment alreadyMoved(Payment payment) {
        log.info("Payment {} was already finalized by someone else; nothing to do", payment.getId());
        return findPayment.execute(payment.getId());
    }
}
