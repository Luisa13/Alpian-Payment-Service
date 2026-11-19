package ch.alpian.payment.infrastructure.persistence;

import ch.alpian.payment.domain.LedgerRepository;
import ch.alpian.payment.domain.Money;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLedgerRepository implements LedgerRepository {

    private final JdbcClient jdbc;

    JdbcLedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void postDebit(UUID paymentId, UUID accountId, Money amount) {
        post(paymentId, accountId, amount.amount().negate());
    }

    @Override
    public void postCredit(UUID paymentId, UUID accountId, Money amount) {
        post(paymentId, accountId, amount.amount());
    }

    private void post(UUID paymentId, UUID accountId, BigDecimal signedAmount) {
        jdbc.sql("""
                        INSERT INTO ledger_entries (id, payment_id, account_id, amount)
                        VALUES (:id, :paymentId, :accountId, :amount)
                        """)
                .param("id", UUID.randomUUID())
                .param("paymentId", paymentId)
                .param("accountId", accountId)
                .param("amount", signedAmount)
                .update();
    }
}
