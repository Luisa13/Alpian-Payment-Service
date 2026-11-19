package ch.alpian.payment.application.support;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Re-runs a whole transaction when PostgreSQL aborts it with a deadlock ({@code 40P01}) or a lock
 * timeout ({@code 55P03}). Limited attempts, exponential backoff with jitter.
 *
 * <p>The supplier must start its own transaction (call a {@code @Transactional} flow through its
 * proxy), so every attempt is a fresh transaction.
 */
@Component
public class TransactionRetry {

    private static final Logger log = LoggerFactory.getLogger(TransactionRetry.class);
    private static final Set<String> RETRYABLE_SQL_STATES = Set.of("40P01", "55P03");

    private final int maxAttempts;
    private final long baseBackoffMs;

    public TransactionRetry(
            @Value("${app.retry.max-attempts:4}") int maxAttempts,
            @Value("${app.retry.base-backoff-ms:20}") long baseBackoffMs) {
        this.maxAttempts = maxAttempts;
        this.baseBackoffMs = baseBackoffMs;
    }

    public <T> T execute(Supplier<T> transaction) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.get();
            } catch (RuntimeException e) {
                if (attempt >= maxAttempts || !isRetryable(e)) {
                    throw e;
                }
                log.warn("Transaction aborted by the database (attempt {}/{}), retrying", attempt, maxAttempts);
                pause(attempt);
            }
        }
    }

    static boolean isRetryable(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && RETRYABLE_SQL_STATES.contains(sql.getSQLState())) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private void pause(int attempt) {
        long ceiling = baseBackoffMs << (attempt - 1);
        long sleep = ThreadLocalRandom.current().nextLong(baseBackoffMs, ceiling + 1);
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying a transaction", e);
        }
    }
}
