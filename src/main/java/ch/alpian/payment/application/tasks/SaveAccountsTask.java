package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountRepository;
import java.util.Collection;
import org.springframework.stereotype.Component;

/** Task: persist accounts that are already locked by the calling flow. */
@Component
public class SaveAccountsTask implements Task<Collection<Account>, Void> {

    private final AccountRepository accounts;

    public SaveAccountsTask(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Void execute(Collection<Account> toSave) {
        toSave.forEach(accounts::save);
        return null;
    }
}
