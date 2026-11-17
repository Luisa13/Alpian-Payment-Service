package ch.example.template.transfer.domain;

import ch.example.template.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Transfer aggregate root.
 *
 * <p>Persistence: Spring Data JDBC maps this directly to the {@code transfers} table. The {@link
 * Money} value object is flattened to two columns ({@code amount_value}, {@code amount_currency}).
 * {@link TransferStatus} is stored as a discriminator string ({@code status_code}) plus an
 * optional {@code failure_reason} column.
 *
 * <p>Spring Data annotations ({@code @Id}, {@code @Version}, {@code @Table}, {@code @Column}) are
 * used here as a pragmatic trade-off; they carry no runtime Spring coupling and are excluded from
 * the ArchUnit framework-independence rule (see {@code ArchitectureTest}).
 *
 * <p>Optimistic locking: the {@code @Version} column is incremented by Spring Data JDBC on every
 * {@code save()}. A concurrent save of a stale version throws {@link
 * org.springframework.dao.OptimisticLockingFailureException}, which is mapped to HTTP 409 by
 * {@code GlobalExceptionHandler}.
 */
@Table("transfers")
public class Transfer {

    @Id
    private UUID id;

    // Money columns: amount_value, amount_currency
    @Column("amount_value")
    private java.math.BigDecimal amountValue;

    @Column("amount_currency")
    private String amountCurrency;

    // Status columns: status_code, failure_reason
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

    // ── Factory ───────────────────────────────────────────────────────────────

    public static Transfer create(Money amount, UUID sourceAccountId, UUID targetAccountId, String description) {
        var t = new Transfer();
        t.id = UUID.randomUUID();
        t.amountValue = amount.amount();
        t.amountCurrency = amount.currency();
        t.statusCode = new TransferStatus.Requested().code();
        t.failureReason = null;
        t.sourceAccountId = sourceAccountId;
        t.targetAccountId = targetAccountId;
        t.description = description;
        t.createdAt = Instant.now();
        t.updatedAt = t.createdAt;
        return t;
    }

    // ── Status transitions ────────────────────────────────────────────────────

    public void settle() {
        requireStatus(TransferStatus.Requested.class, "settle");
        this.statusCode = new TransferStatus.Settled().code();
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        requireStatus(TransferStatus.Requested.class, "fail");
        this.statusCode = new TransferStatus.Failed(reason).code();
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    private void requireStatus(Class<? extends TransferStatus> expected, String operation) {
        if (!expected.isInstance(getTransferStatus())) {
            throw new IllegalStateException("Cannot " + operation + " a transfer in status '" + statusCode + "'");
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public Money getAmount() {
        return new Money(amountValue, amountCurrency);
    }

    public TransferStatus getTransferStatus() {
        return TransferStatus.fromCode(statusCode, failureReason);
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
