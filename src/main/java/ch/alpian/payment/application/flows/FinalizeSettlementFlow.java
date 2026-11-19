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
        if (payment.getStatus() != PaymentStatus.RESERVED) {
            return alreadyMoved(payment);
        }
        return switch (outcome.status()) {
            case SETTLED -> complete(payment, outcome.reference());
            case REJECTED -> fail(payment);
            case UNKNOWN -> {
                recordAttempt.execute(payment);
                yield payment;
            }
        };
    }

    private Payment complete(Payment payment, String railReference) {
        payment.complete(railReference);
        if (!transitionPayment.execute(new TransitionPaymentTask.Arguments(payment, PaymentStatus.RESERVED))) {
            return alreadyMoved(payment);
        }

        UUID receiverAccountId = resolveReceiver.execute(payment);
        Map<UUID, Account> locked = lockAccounts.execute(List.of(payment.getSenderAccountId(), receiverAccountId));
        Account sender = locked.get(payment.getSenderAccountId());
        Account receiver = locked.get(receiverAccountId);
        sender.settleDebit(payment.getAmount());
        receiver.credit(payment.getAmount());
        saveAccounts.execute(List.of(sender, receiver));

        postLedger.execute(new PostLedgerEntriesTask.Arguments(payment, receiverAccountId));
        writeOutbox.execute(payment);
        return payment;
    }

    private Payment fail(Payment payment) {
        payment.fail(FailureReason.RAIL_REJECTED);
        if (!transitionPayment.execute(new TransitionPaymentTask.Arguments(payment, PaymentStatus.RESERVED))) {
            return alreadyMoved(payment);
        }

        Account sender =
                lockAccounts.execute(List.of(payment.getSenderAccountId())).get(payment.getSenderAccountId());
        sender.release(payment.getAmount());
        saveAccounts.execute(List.of(sender));

        writeOutbox.execute(payment);
        return payment;
    }

    private Payment alreadyMoved(Payment payment) {
        log.info("Payment {} was already finalized by someone else; nothing to do", payment.getId());
        return findPayment.execute(payment.getId());
    }
}
