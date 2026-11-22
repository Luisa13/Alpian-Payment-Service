package ch.alpian.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** T2: the schema and seed data satisfy every invariant, and the database refuses violations. */
class SeedDataIT extends PaymentIntegrationTest {

    private static final UUID ALICE = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Test
    void seed_has_customer_accounts_and_chf_clearing_shards() {
        long customers = jdbc.sql("SELECT COUNT(*) FROM accounts WHERE account_type = 'CUSTOMER'")
                .query(Long.class)
                .single();
        long clearing = jdbc.sql("SELECT COUNT(*) FROM accounts WHERE account_type = 'CLEARING' AND currency = 'CHF'")
                .query(Long.class)
                .single();
        assertThat(customers).isGreaterThanOrEqualTo(8);
        assertThat(clearing).isEqualTo(8);
    }

    @Test
    void every_seeded_account_has_available_equal_to_balance_and_never_negative() {
        List<Long> violations = jdbc.sql("""
                        SELECT COUNT(*) FROM accounts
                        WHERE owner_name <> 'IT account'
                          AND (balance < 0 OR available_balance < 0 OR available_balance <> balance)
                        """).query(Long.class).list();
        assertThat(violations).containsExactly(0L);
    }

    @Test
    void every_account_balance_equals_the_sum_of_its_ledger_entries() {
        long mismatches = jdbc.sql("""
                        SELECT COUNT(*) FROM accounts a
                        WHERE a.balance <> COALESCE((SELECT SUM(l.amount) FROM ledger_entries l WHERE l.account_id = a.id), 0)
                        """).query(Long.class).single();
        assertThat(mismatches).isZero();
    }

    @Test
    void the_whole_ledger_nets_to_the_total_of_all_balances() {
        BigDecimal ledger = jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM ledger_entries")
                .query(BigDecimal.class)
                .single();
        BigDecimal balances = jdbc.sql("SELECT COALESCE(SUM(balance), 0) FROM accounts")
                .query(BigDecimal.class)
                .single();
        assertThat(ledger).isEqualByComparingTo(balances);
    }

    @Test
    void opening_ledger_entries_have_no_payment_and_clearing_starts_at_zero() {
        assertThat(ledgerSum(ALICE)).isEqualByComparingTo("10000.00");
        assertThat(clearingAccountIds())
                .allSatisfy(id -> assertThat(ledgerSum(id).signum()).isGreaterThanOrEqualTo(0));
        long openingForAlice = jdbc.sql(
                        "SELECT COUNT(*) FROM ledger_entries WHERE account_id = :id AND payment_id IS NULL")
                .param("id", ALICE)
                .query(Long.class)
                .single();
        assertThat(openingForAlice).isEqualTo(1);
    }

    @Test
    void database_refuses_a_negative_balance() {
        assertThatThrownBy(() -> insertAccount("CUSTOMER", "-1.00", "-1.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_refuses_available_balance_above_balance() {
        assertThatThrownBy(() -> insertAccount("CUSTOMER", "10.00", "10.01"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_refuses_an_unknown_account_type() {
        assertThatThrownBy(() -> insertAccount("SAVINGS", "1.00", "1.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_refuses_an_unknown_payment_status() {
        UUID sender = newAccount("10.00");
        assertThatThrownBy(() -> insertPayment(
                        sender,
                        "INTERNAL",
                        ALICE,
                        null,
                        "1.00",
                        "PENDING",
                        UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_refuses_a_payment_with_both_or_no_receiver() {
        UUID sender = newAccount("10.00");
        assertThatThrownBy(() -> insertPayment(
                        sender,
                        "INTERNAL",
                        ALICE,
                        "CH9300762011623852957",
                        "1.00",
                        "RESERVED",
                        UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPayment(
                        sender,
                        "EXTERNAL",
                        null,
                        null,
                        "1.00",
                        "RESERVED",
                        UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_refuses_a_payment_to_oneself() {
        UUID sender = newAccount("10.00");
        assertThatThrownBy(() -> insertPayment(
                        sender,
                        "INTERNAL",
                        sender,
                        null,
                        "1.00",
                        "RESERVED",
                        UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_refuses_a_zero_amount_payment() {
        UUID sender = newAccount("10.00");
        assertThatThrownBy(() -> insertPayment(
                        sender,
                        "INTERNAL",
                        ALICE,
                        null,
                        "0.00",
                        "RESERVED",
                        UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_refuses_a_reused_idempotency_key() {
        UUID sender = newAccount("10.00");
        String key = UUID.randomUUID().toString();
        insertPayment(sender, "INTERNAL", ALICE, null, "1.00", "RESERVED", key);
        assertThatThrownBy(() -> insertPayment(sender, "INTERNAL", ALICE, null, "1.00", "RESERVED", key))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_refuses_a_zero_ledger_entry_and_double_posting_per_account() {
        UUID sender = newAccount("10.00");
        UUID payment = insertPayment(
                sender,
                "INTERNAL",
                ALICE,
                null,
                "1.00",
                "RESERVED",
                UUID.randomUUID().toString());
        assertThatThrownBy(() -> insertLedger(payment, sender, "0.00"))
                .isInstanceOf(DataIntegrityViolationException.class);

        try {
            insertLedger(payment, sender, "-1.00");
            assertThatThrownBy(() -> insertLedger(payment, sender, "-1.00"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.sql("DELETE FROM ledger_entries WHERE payment_id = :payment")
                    .param("payment", payment)
                    .update();
            jdbc.sql("DELETE FROM payments WHERE id = :payment")
                    .param("payment", payment)
                    .update();
        }
    }

    @Test
    void amounts_are_stored_with_four_decimals() {
        UUID id = newAccount("12.3456");
        assertThat(balance(id)).isEqualByComparingTo("12.3456");
    }

    private void insertAccount(String type, String balance, String available) {
        jdbc.sql("""
                        INSERT INTO accounts (id, owner_name, account_type, currency, balance, available_balance)
                        VALUES (:id, 'x', :type, 'CHF', :balance, :available)
                        """)
                .param("id", UUID.randomUUID())
                .param("type", type)
                .param("balance", new BigDecimal(balance))
                .param("available", new BigDecimal(available))
                .update();
    }

    private UUID insertPayment(
            UUID sender,
            String receiverType,
            UUID receiverAccount,
            String iban,
            String amount,
            String status,
            String key) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO payments (id, idempotency_key, request_hash, sender_account_id, receiver_type,
                                              receiver_account_id, receiver_iban, amount, currency, status)
                        VALUES (:id, :key, :hash, :sender, :receiverType, :receiverAccount, :iban, :amount, 'CHF', :status)
                        """)
                .param("id", id)
                .param("key", key)
                .param("hash", "0".repeat(64))
                .param("sender", sender)
                .param("receiverType", receiverType)
                .param("receiverAccount", receiverAccount, java.sql.Types.OTHER)
                .param("iban", iban)
                .param("amount", new BigDecimal(amount))
                .param("status", status)
                .update();
        return id;
    }

    private void insertLedger(UUID payment, UUID account, String amount) {
        jdbc.sql(
                        "INSERT INTO ledger_entries (id, payment_id, account_id, amount) VALUES (:id, :payment, :account, :amount)")
                .param("id", UUID.randomUUID())
                .param("payment", payment)
                .param("account", account)
                .param("amount", new BigDecimal(amount))
                .update();
    }
}
