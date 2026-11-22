package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.Payment;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Task: the account that gets the money: the internal receiver, or a clearing shard for an IBAN. */
@Component
public class ResolveReceiverAccountTask implements Task<Payment, UUID> {

    private final AccountRepository accounts;

    public ResolveReceiverAccountTask(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UUID execute(Payment payment) {
        if (payment.getReceiver().isInternal()) {
            return payment.getReceiver().accountId();
        }
        String currency = payment.getAmount().currency();
        List<UUID> clearingIds = accounts.findClearingAccountIds(currency);
        if (clearingIds.isEmpty()) {
            throw new IllegalStateException("No clearing account configured for currency " + currency);
        }
        int shardIndex = Math.floorMod(payment.getId().hashCode(), clearingIds.size());
        return clearingIds.get(shardIndex);
    }
}
