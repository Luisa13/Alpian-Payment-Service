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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
 * <p>A ShedLock distributed lock ({@code recoveryJob}) ensures at most one replica runs recovery
 * at a time, preventing duplicate external rail calls across replicas. Each batch enforces a 90s
 * time budget so processing finishes well within the 2-minute {@code lockAtMostFor} window; any
 * remaining payments are deferred to the next cycle. {@link FinalizeSettlementFlow} also uses a
 * guarded {@code UPDATE … WHERE status = 'RESERVED'} as a safety net so at most one finalization
 * can ever commit for a given payment.
 */
@Component
class RecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(RecoveryJob.class);
    private static final int BATCH_SIZE = 50;
    private static final Duration MAX_BATCH_DURATION = Duration.ofSeconds(90);

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

    @Scheduled(
            fixedDelayString = "${app.recovery.interval-ms}",
            initialDelayString = "${app.recovery.initial-delay-ms:${app.recovery.interval-ms}}")
    @SchedulerLock(
            name = "recoveryJob",
            lockAtMostFor = "${app.recovery.lock-at-most:PT2M}",
            lockAtLeastFor = "${app.recovery.lock-at-least:PT5S}")
    public void recover() {
        Instant threshold = Instant.now().minus(properties.staleAfter());
        List<Payment> stale = readOnlyTx.execute(
                status -> payments.findStaleReserved(threshold, properties.maxAttempts(), BATCH_SIZE));
        if (stale == null || stale.isEmpty()) return;

        log.info("Recovery: found {} stale RESERVED payment(s) to retry", stale.size());
        Instant deadline = Instant.now().plus(MAX_BATCH_DURATION);
        for (Payment payment : stale) {
            if (Instant.now().isAfter(deadline)) {
                log.warn(
                        "Recovery batch hit {} time budget; remaining payments deferred to next cycle",
                        MAX_BATCH_DURATION);
                break;
            }
            retryOne(payment);
        }
    }

    private void retryOne(Payment payment) {
        try {
            // Check whether the rail already processed the payment before re-submitting.
            // If the initial settle() timed out the rail may have recorded a SETTLED/REJECTED outcome
            // that we never saw. Re-submitting in that case would be a semantic duplicate.
            SettlementOutcome status = callRail.getStatus(payment);
            SettlementOutcome outcome = status.status() == SettlementOutcome.Status.UNKNOWN
                    ? callRail.execute(payment) // rail has no record; re-submit
                    : status; // rail has a definitive answer; use it directly
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
