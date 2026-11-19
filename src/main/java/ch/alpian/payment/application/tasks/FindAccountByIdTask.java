package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.EntityNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Task: resolve an {@link Account} by id or throw {@link EntityNotFoundException}. */
@Component
public class FindAccountByIdTask implements Task<UUID, Account> {

    private final AccountRepository accounts;

    public FindAccountByIdTask(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Account execute(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new EntityNotFoundException("Account", id));
    }
}
