package ch.example.template.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Payment aggregate root.
 *
 * <p>Persistence: Spring Data JDBC maps this directly to the {@code payments} table. The {@link
 * Money} value object is flattened to two columns ({@code amount_value}, {@code amount_currency}).
 * {@link PaymentStatus} is stored as a discriminator string ({@code status_code}) plus an optional
 * {@code failure_reason} column.
 *
 * <p>Optimistic locking: the {@code @Version} column is incremented by Spring Data JDBC on every
 * {@code save()}. A concurrent save of a stale version throws {@link
 * org.springframework.dao.OptimisticLockingFailureException}, which is mapped to HTTP 409 by
 * {@code GlobalExceptionHandler}.
 */
@Table("payments")
public class Payment {

    @Id
    private UUID id;

    @Column("amount_value")
    private java.math.BigDecimal amountValue;

    @Column("amount_currency")
    private String amountCurrency;

    @Column("status_code")
    private String statusCode;

    @Column("failure_reason")
    private String failureReason;

    @Column("source_account_id")
    private UUID sourceAccountId;

    @Column("target_account_id")
    private UUID targetAccountId;

    private String description;

    @Version
    private Long version;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    public static Payment create(Money amount, UUID sourceAccountId, UUID targetAccountId, String description) {
        var p = new Payment();
        p.id = UUID.randomUUID();
        p.amountValue = amount.amount();
        p.amountCurrency = amount.currency();
        p.statusCode = new PaymentStatus.Requested().code();
        p.failureReason = null;
        p.sourceAccountId = sourceAccountId;
        p.targetAccountId = targetAccountId;
        p.description = description;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    public void settle() {
        requireStatus(PaymentStatus.Requested.class, "settle");
        this.statusCode = new PaymentStatus.Settled().code();
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        requireStatus(PaymentStatus.Requested.class, "fail");
        this.statusCode = new PaymentStatus.Failed(reason).code();
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    private void requireStatus(Class<? extends PaymentStatus> expected, String operation) {
        if (!expected.isInstance(getPaymentStatus())) {
            throw new IllegalStateException("Cannot " + operation + " a payment in status '" + statusCode + "'");
        }
    }

    public UUID getId() {
        return id;
    }

    public Money getAmount() {
        return new Money(amountValue, amountCurrency);
    }

    public PaymentStatus getPaymentStatus() {
        return PaymentStatus.fromCode(statusCode, failureReason);
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getTargetAccountId() {
        return targetAccountId;
    }

    public String getDescription() {
        return description;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
