package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.domain.exception.CurrencyMismatchException;
import ch.alpian.payment.domain.exception.UnknownAccountException;
import org.springframework.stereotype.Component;

/** Task: an internal receiver must exist and hold the payment currency. External receivers pass. */
@Component
public class ValidateReceiverTask implements Task<ValidateReceiverTask.Arguments, Void> {

    private final AccountRepository accounts;

    public ValidateReceiverTask(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public record Arguments(Receiver receiver, String currency) {}

    @Override
    public Void execute(Arguments args) {
        if (!args.receiver().isInternal()) {
            return null;
        }
        Account receiver = accounts.findById(args.receiver().accountId())
                .orElseThrow(() -> new UnknownAccountException(args.receiver().accountId()));
        if (!receiver.getCurrency().equals(args.currency())) {
            throw new CurrencyMismatchException(receiver.getId(), receiver.getCurrency(), args.currency());
        }
        return null;
    }
}
