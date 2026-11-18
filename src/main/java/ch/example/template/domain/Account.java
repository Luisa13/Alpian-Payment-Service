package ch.example.template.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Account aggregate root.
 *
 * <p>Represents a financial account that can send and receive payments. Accounts are seeded via
 * Flyway migration V5; in production they would be created by a separate account-management
 * service.
 */
@Table("accounts")
public class Account {

    @Id
    private UUID id;

    private String owner;

    @Column("balance_value")
    private java.math.BigDecimal balanceValue;

    @Column("balance_currency")
    private String balanceCurrency;

    @Column("created_at")
    private Instant createdAt;

    public static Account create(String owner, Money balance) {
        var a = new Account();
        a.id = UUID.randomUUID();
        a.owner = owner;
        a.balanceValue = balance.amount();
        a.balanceCurrency = balance.currency();
        a.createdAt = Instant.now();
        return a;
    }

    public UUID getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public Money getBalance() {
        return new Money(balanceValue, balanceCurrency);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
