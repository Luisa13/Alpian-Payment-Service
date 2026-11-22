package ch.alpian.payment.infrastructure.recovery;

import ch.alpian.payment.application.flows.FinalizeSettlementFlow;
import ch.alpian.payment.application.support.TransactionRetry;
import ch.alpian.payment.application.tasks.CallSettlementRailTask;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentRepository;
import ch.alpian.payment.domain.SettlementOutcome;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background job that retries settlement for payments stuck in {@code RESERVED}.
 *
 * <p>A payment lands in {@code RESERVED} and stays there when the inline rail call returns
 * {@code UNKNOWN} (timeout or transient error). This job scans for such payments periodically and
 * calls the rail again with the same payment id, which the rail treats as an idempotent retry of
 * the same transfer.
 *
 * <p>Payments older than {@code app.recovery.stale-after} and with fewer than
 * {@code app.recovery.max-attempts} settlement attempts are eligible. Payments that have exhausted
 * the attempt cap are left untouched and surfaced via the {@code payments.recovery.stuck} gauge for
 * manual review — they are never auto-failed without the rail confirming the money did not move.
 *
 * <p>Multiple instances running this job concurrently are safe: {@link FinalizeSettlementFlow} uses
 * a guarded {@code UPDATE … WHERE status = 'RESERVED'}, so at most one instance finalizes any
 * given payment.
 */
@Component
class RecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(RecoveryJob.class);
    private static final int BATCH_SIZE = 50;

    private final PaymentRepository payments;
    private final CallSettlementRailTask callRail;
    private final FinalizeSettlementFlow finalizeSettlement;
    private final TransactionRetry retry;
    private final TransactionTemplate readOnlyTx;
    private final JdbcClient jdbc;
    private final RecoveryProperties properties;

    RecoveryJob(
            PaymentRepository payments,
            CallSettlementRailTask callRail,
            FinalizeSettlementFlow finalizeSettlement,
            TransactionRetry retry,
            PlatformTransactionManager txManager,
            JdbcClient jdbc,
            MeterRegistry meters,
            RecoveryProperties properties) {
        this.payments = payments;
        this.callRail = callRail;
        this.finalizeSettlement = finalizeSettlement;
        this.retry = retry;
        this.readOnlyTx = new TransactionTemplate(txManager);
        this.readOnlyTx.setReadOnly(true);
        this.jdbc = jdbc;
        this.properties = properties;

        Gauge.builder("payments.recovery.stuck", this, RecoveryJob::countStuck)
                .description("RESERVED payments that have exhausted recovery attempts and need manual review")
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${app.recovery.interval-ms}")
    public void recover() {
        Instant threshold = Instant.now().minus(properties.staleAfter());
        List<Payment> stale = readOnlyTx.execute(
                status -> payments.findStaleReserved(threshold, properties.maxAttempts(), BATCH_SIZE));
        if (stale == null || stale.isEmpty()) return;

        log.info("Recovery: found {} stale RESERVED payment(s) to retry", stale.size());
        for (Payment payment : stale) {
            retryOne(payment);
        }
    }

    private void retryOne(Payment payment) {
        // Check whether the rail already processed the payment before re-submitting.
        // If the initial settle() timed out the rail may have recorded a SETTLED/REJECTED outcome
        // that we never saw. Re-submitting in that case would be a semantic duplicate.
        SettlementOutcome status = callRail.getStatus(payment);
        SettlementOutcome outcome = status.status() == SettlementOutcome.Status.UNKNOWN
                ? callRail.execute(payment) // rail has no record; re-submit
                : status; // rail has a definitive answer; use it directly
        try {
            retry.execute(() -> finalizeSettlement.finalizeSettlement(payment.getId(), outcome));
        } catch (RuntimeException e) {
            log.warn("Recovery attempt failed for payment {} (will retry next cycle)", payment.getId(), e);
        }
    }

    private double countStuck() {
        Instant threshold = Instant.now().minus(properties.staleAfter());
        Long count = jdbc.sql("""
                        SELECT COUNT(*) FROM payments
                        WHERE status = 'RESERVED'
                          AND reserved_at < :before
                          AND settlement_attempts >= :maxAttempts
                        """)
                .param("before", Timestamp.from(threshold))
                .param("maxAttempts", properties.maxAttempts())
                .query(Long.class)
                .single();
        return count == null ? 0 : count;
    }
}
