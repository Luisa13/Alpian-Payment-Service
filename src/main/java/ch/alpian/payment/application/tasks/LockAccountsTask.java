package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.exception.UnknownAccountException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Task: lock several accounts in ascending id order, so concurrent A→B and B→A payments cannot
 * deadlock. Returns the accounts keyed by id.
 */
@Component
public class LockAccountsTask implements Task<Collection<UUID>, Map<UUID, Account>> {

    private final AccountRepository accounts;

    public LockAccountsTask(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Map<UUID, Account> execute(Collection<UUID> ids) {
        var sortedIds = new TreeSet<>(ids);
        Map<UUID, Account> locked = new LinkedHashMap<>();
        accounts.findAllByIdForUpdate(sortedIds).forEach(a -> locked.put(a.getId(), a));
        for (UUID id : sortedIds) {
            if (!locked.containsKey(id)) {
                throw new UnknownAccountException(id);
            }
        }
        return locked;
    }
}
