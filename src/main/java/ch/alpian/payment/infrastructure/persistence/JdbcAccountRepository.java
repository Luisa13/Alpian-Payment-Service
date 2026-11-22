package ch.alpian.payment.infrastructure.persistence;

import static ch.alpian.payment.infrastructure.persistence.JdbcSupport.uuid;

import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.AccountType;
import ch.alpian.payment.domain.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountRepository {

    private static final String SELECT =
            "SELECT id, owner_name, account_type, currency, balance, available_balance, version FROM accounts";

    private final JdbcClient jdbc;

    JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("id", id)
                .query(JdbcAccountRepository::map)
                .optional();
    }

    @Override
    public Optional<Account> findByIdForUpdate(UUID id) {
        return jdbc.sql(SELECT + " WHERE id = :id FOR NO KEY UPDATE")
                .param("id", id)
                .query(JdbcAccountRepository::map)
                .optional();
    }

    @Override
    public List<Account> findAllByIdForUpdate(Collection<UUID> ids) {
        return jdbc.sql(SELECT + " WHERE id IN (:ids) ORDER BY id FOR NO KEY UPDATE")
                .param("ids", ids)
                .query(JdbcAccountRepository::map)
                .list();
    }

    @Override
    public List<UUID> findClearingAccountIds(String currency) {
        return jdbc.sql("""
                        SELECT id FROM accounts
                        WHERE account_type = 'CLEARING' AND currency = :currency
                        ORDER BY id
                        """)
                .param("currency", currency)
                .query((rs, n) -> uuid(rs, "id"))
                .list();
    }

    @Override
    public void save(Account account) {
        jdbc.sql("""
                        UPDATE accounts
                        SET balance = :balance, available_balance = :available,
                            version = version + 1, updated_at = now()
                        WHERE id = :id
                        """)
                .param("balance", account.getBalance().amount())
                .param("available", account.getAvailableBalance().amount())
                .param("id", account.getId())
                .update();
    }

    private static Account map(ResultSet rs, int rowNum) throws SQLException {
        String currency = rs.getString("currency");
        return new Account(
                uuid(rs, "id"),
                rs.getString("owner_name"),
                AccountType.valueOf(rs.getString("account_type")),
                new Money(rs.getBigDecimal("balance"), currency),
                new Money(rs.getBigDecimal("available_balance"), currency),
                rs.getLong("version"));
    }
}
