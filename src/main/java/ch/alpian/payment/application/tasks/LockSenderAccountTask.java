package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.CurrencyMismatchException;
import ch.alpian.payment.domain.UnknownAccountException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Task: lock the sender row ({@code SELECT ... FOR UPDATE}); it must exist and match the currency. */
@Component
public class LockSenderAccountTask implements Task<LockSenderAccountTask.Arguments, Account> {

    private final AccountRepository accounts;

    public LockSenderAccountTask(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public record Arguments(UUID senderAccountId, String currency) {}

    @Override
    public Account execute(Arguments args) {
        Account sender = accounts.findByIdForUpdate(args.senderAccountId())
                .orElseThrow(() -> new UnknownAccountException(args.senderAccountId()));
        if (!sender.getCurrency().equals(args.currency())) {
            throw new CurrencyMismatchException(sender.getId(), sender.getCurrency(), args.currency());
        }
        return sender;
    }
}
