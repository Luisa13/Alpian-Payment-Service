package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.alpian.payment.application.support.TransactionRetry;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DeadlockLoserDataAccessException;

class TransactionRetryTest {

    private final TransactionRetry retry = new TransactionRetry(3, 1);

    private static RuntimeException deadlock() {
        return new DeadlockLoserDataAccessException("deadlock", new SQLException("deadlock detected", "40P01"));
    }

    @Test
    void returns_the_result_when_nothing_fails() {
        assertThat(retry.execute(() -> "ok")).isEqualTo("ok");
    }

    @Test
    void retries_on_deadlock_and_then_succeeds() {
        var calls = new AtomicInteger();
        String result = retry.execute(() -> {
            if (calls.incrementAndGet() < 3) throw deadlock();
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void retries_on_lock_timeout() {
        var calls = new AtomicInteger();
        retry.execute(() -> {
            if (calls.incrementAndGet() < 2) {
                throw new RuntimeException("wrapped", new SQLException("lock timeout", "55P03"));
            }
            return "ok";
        });
        assertThat(calls).hasValue(2);
    }

    @Test
    void gives_up_after_the_maximum_number_of_attempts() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> retry.execute(() -> {
                    calls.incrementAndGet();
                    throw deadlock();
                }))
                .isInstanceOf(DeadlockLoserDataAccessException.class);
        assertThat(calls).hasValue(3);
    }

    @Test
    void does_not_retry_other_errors() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> retry.execute(() -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void does_not_retry_other_sql_states() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> retry.execute(() -> {
                    calls.incrementAndGet();
                    throw new RuntimeException(new SQLException("unique violation", "23505"));
                }))
                .isInstanceOf(RuntimeException.class);
        assertThat(calls).hasValue(1);
    }
}
