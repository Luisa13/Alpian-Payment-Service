package ch.alpian.payment.integration;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Base for integration tests that touch the database: helpers to create isolated accounts (every
 * test works on its own accounts, so tests never interfere) and to read back balances.
 */
public abstract class PaymentIntegrationTest extends IntegrationTestBase {

    @Autowired
    protected JdbcClient jdbc;

    /** Creates a CHF customer account with this opening balance (and its opening ledger entry). */
    protected UUID newAccount(String balance) {
        UUID id = UUID.randomUUID();
        BigDecimal amount = new BigDecimal(balance);
        jdbc.sql("""
                        INSERT INTO accounts (id, owner_name, account_type, currency, balance, available_balance)
                        VALUES (:id, 'IT account', 'CUSTOMER', 'CHF', :amount, :amount)
                        """).param("id", id).param("amount", amount).update();
        if (amount.signum() != 0) {
            jdbc.sql(
                            "INSERT INTO ledger_entries (id, payment_id, account_id, amount) VALUES (:id, NULL, :account, :amount)")
                    .param("id", UUID.randomUUID())
                    .param("account", id)
                    .param("amount", amount)
                    .update();
        }
        return id;
    }

    protected BigDecimal balance(UUID accountId) {
        return jdbc.sql("SELECT balance FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(BigDecimal.class)
                .single();
    }

    protected BigDecimal available(UUID accountId) {
        return jdbc.sql("SELECT available_balance FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(BigDecimal.class)
                .single();
    }

    protected BigDecimal ledgerSum(UUID accountId) {
        return jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM ledger_entries WHERE account_id = :id")
                .param("id", accountId)
                .query(BigDecimal.class)
                .single();
    }

    protected List<UUID> clearingAccountIds() {
        return jdbc.sql("SELECT id FROM accounts WHERE account_type = 'CLEARING' AND currency = 'CHF' ORDER BY id")
                .query(UUID.class)
                .list();
    }

    protected BigDecimal totalClearingBalance() {
        return jdbc.sql(
                        "SELECT COALESCE(SUM(balance), 0) FROM accounts WHERE account_type = 'CLEARING' AND currency = 'CHF'")
                .query(BigDecimal.class)
                .single();
    }

    protected long count(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Long.class).single();
    }

    protected long ledgerRows(UUID paymentId) {
        return count("SELECT COUNT(*) FROM ledger_entries WHERE payment_id = :id", paymentId);
    }

    protected long outboxEvents(UUID paymentId, String eventType) {
        return jdbc.sql("SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = :id AND event_type = :type")
                .param("id", paymentId)
                .param("type", eventType)
                .query(Long.class)
                .single();
    }

    protected long outboxEvents(UUID paymentId) {
        return count("SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = :id", paymentId);
    }

    protected static String bd(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }
}
