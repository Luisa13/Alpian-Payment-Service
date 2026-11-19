package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.LedgerRepository;
import ch.alpian.payment.domain.Payment;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Task: post the two ledger entries of a completed payment (debit sender, credit receiver). */
@Component
public class PostLedgerEntriesTask implements Task<PostLedgerEntriesTask.Arguments, Void> {

    private final LedgerRepository ledger;

    public PostLedgerEntriesTask(LedgerRepository ledger) {
        this.ledger = ledger;
    }

    public record Arguments(Payment payment, UUID receiverAccountId) {}

    @Override
    public Void execute(Arguments args) {
        Payment payment = args.payment();
        ledger.postDebit(payment.getId(), payment.getSenderAccountId(), payment.getAmount());
        ledger.postCredit(payment.getId(), args.receiverAccountId(), payment.getAmount());
        return null;
    }
}
