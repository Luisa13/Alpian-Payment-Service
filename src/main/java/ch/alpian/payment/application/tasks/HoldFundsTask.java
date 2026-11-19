package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.Money;
import org.springframework.stereotype.Component;

/** Task: put funds on hold on an account that is already locked, and persist it. */
@Component
public class HoldFundsTask implements Task<HoldFundsTask.Arguments, Void> {

    private final AccountRepository accounts;

    public HoldFundsTask(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public record Arguments(Account account, Money amount) {}

    @Override
    public Void execute(Arguments args) {
        args.account().hold(args.amount());
        accounts.save(args.account());
        return null;
    }
}
