package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.Payment;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Task: the account that gets the money: the internal receiver, or the clearing account for an IBAN. */
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
        return accounts.findClearingAccountId(currency)
                .orElseThrow(() ->
                        new IllegalStateException("No clearing account configured for currency " + currency));
    }
}
