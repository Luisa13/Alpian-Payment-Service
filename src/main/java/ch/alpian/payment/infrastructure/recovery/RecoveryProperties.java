package ch.alpian.payment.infrastructure.recovery;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning knobs for the stale-payment recovery job.
 *
 * @param intervalMs  how often the recovery job runs (milliseconds)
 * @param staleAfter  how old a {@code RESERVED} payment must be before the job retries it; must be
 *                    well above the rail call timeout so a payment that is still in-flight inline is
 *                    never picked up by recovery
 * @param maxAttempts payments that have already been retried this many times without a definitive
 *                    rail answer are left alone and surfaced via a metric for manual review
 */
@ConfigurationProperties("app.recovery")
public record RecoveryProperties(long intervalMs, Duration staleAfter, int maxAttempts) {

    public RecoveryProperties {
        if (staleAfter == null) staleAfter = Duration.ofSeconds(30);
        if (maxAttempts <= 0) throw new IllegalArgumentException("max-attempts must be positive");
    }
}
