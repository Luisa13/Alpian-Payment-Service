package ch.alpian.payment.infrastructure.persistence;

import static ch.alpian.payment.infrastructure.persistence.JdbcSupport.instant;
import static ch.alpian.payment.infrastructure.persistence.JdbcSupport.timestamp;
import static ch.alpian.payment.infrastructure.persistence.JdbcSupport.uuid;

import ch.alpian.payment.domain.DuplicateIdempotencyKeyException;
import ch.alpian.payment.domain.FailureReason;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentRepository;
import ch.alpian.payment.domain.PaymentStatus;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.domain.ReceiverType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPaymentRepository implements PaymentRepository {

    private static final String IDEMPOTENCY_CONSTRAINT = "uq_payments_idempotency_key";

    private static final String SELECT = """
            SELECT id, idempotency_key, request_hash, sender_account_id, receiver_type, receiver_account_id,
                   receiver_iban, amount, currency, status, failure_reason, rail_reference, settlement_attempts,
                   version, reserved_at, finalized_at, created_at, updated_at
            FROM payments
            """;

    private final JdbcClient jdbc;

    JdbcPaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Payment p) {
        try {
            jdbc.sql("""
                    INSERT INTO payments (id, idempotency_key, request_hash, sender_account_id, receiver_type,
                                          receiver_account_id, receiver_iban, amount, currency, status,
                                          failure_reason, reserved_at, finalized_at, created_at, updated_at)
                    VALUES (:id, :key, :hash, :sender, :receiverType, :receiverAccount, :receiverIban, :amount,
                            :currency, :status, :failureReason, :reservedAt, :finalizedAt, :createdAt, :updatedAt)
                    """)
                    .param("id", p.getId())
                    .param("key", p.getIdempotencyKey())
                    .param("hash", p.getRequestHash())
                    .param("sender", p.getSenderAccountId())
                    .param("receiverType", p.getReceiver().type().name())
                    .param("receiverAccount", p.getReceiver().accountId(), java.sql.Types.OTHER)
                    .param("receiverIban", p.getReceiver().iban())
                    .param("amount", p.getAmount().amount())
                    .param("currency", p.getAmount().currency())
                    .param("status", p.getStatus().name())
                    .param("failureReason", p.getFailureReason() == null ? null : p.getFailureReason().name())
                    .param("reservedAt", timestamp(p.getReservedAt()))
                    .param("finalizedAt", timestamp(p.getFinalizedAt()))
                    .param("createdAt", timestamp(p.getCreatedAt()))
                    .param("updatedAt", timestamp(p.getUpdatedAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            if (e.getMessage() != null && e.getMessage().contains(IDEMPOTENCY_CONSTRAINT)) {
                throw new DuplicateIdempotencyKeyException(p.getIdempotencyKey(), e);
            }
            throw e;
        }
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("id", id)
                .query(JdbcPaymentRepository::map)
                .optional();
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql(SELECT + " WHERE idempotency_key = :key")
                .param("key", idempotencyKey)
                .query(JdbcPaymentRepository::map)
                .optional();
    }

    @Override
    public boolean applyTransition(Payment p, PaymentStatus from) {
        int updated = jdbc.sql("""
                        UPDATE payments
                        SET status = :to, failure_reason = :failureReason, rail_reference = :railReference,
                            finalized_at = :finalizedAt, version = version + 1, updated_at = now()
                        WHERE id = :id AND status = :from
                        """)
                .param("to", p.getStatus().name())
                .param("failureReason", p.getFailureReason() == null ? null : p.getFailureReason().name())
                .param("railReference", p.getRailReference())
                .param("finalizedAt", timestamp(p.getFinalizedAt()))
                .param("id", p.getId())
                .param("from", from.name())
                .update();
        return updated == 1;
    }

    @Override
    public void recordSettlementAttempt(UUID paymentId) {
        jdbc.sql("""
                        UPDATE payments
                        SET settlement_attempts = settlement_attempts + 1, version = version + 1, updated_at = now()
                        WHERE id = :id AND status = 'RESERVED'
                        """)
                .param("id", paymentId)
                .update();
    }

    private static Payment map(ResultSet rs, int rowNum) throws SQLException {
        ReceiverType type = ReceiverType.valueOf(rs.getString("receiver_type"));
        Receiver receiver = type == ReceiverType.INTERNAL
                ? Receiver.internal(uuid(rs, "receiver_account_id"))
                : Receiver.external(rs.getString("receiver_iban"));
        String failureReason = rs.getString("failure_reason");
        return Payment.restore(
                uuid(rs, "id"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                uuid(rs, "sender_account_id"),
                receiver,
                new Money(rs.getBigDecimal("amount"), rs.getString("currency")),
                PaymentStatus.valueOf(rs.getString("status")),
                failureReason == null ? null : FailureReason.valueOf(failureReason),
                rs.getString("rail_reference"),
                rs.getInt("settlement_attempts"),
                rs.getLong("version"),
                instant(rs, "reserved_at"),
                instant(rs, "finalized_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }
}
