package ch.alpian.payment.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port: storage of accounts. The {@code ForUpdate} methods take pessimistic row locks. */
public interface AccountRepository {

    Optional<Account> findById(UUID id);

    /** Locks the row ({@code SELECT ... FOR UPDATE}) until the surrounding transaction ends. */
    Optional<Account> findByIdForUpdate(UUID id);

    /** Locks all rows in ascending id order, so concurrent callers can never deadlock. */
    List<Account> findAllByIdForUpdate(Collection<UUID> ids);

    /** All clearing account shards that are counterparts of external payments in this currency, ordered by id. */
    List<UUID> findClearingAccountIds(String currency);

    /** Persists balance and available balance of an account loaded {@code FOR UPDATE}. */
    void save(Account account);
}
