package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.alpian.payment.application.tasks.CheckIdempotencyTask;
import ch.alpian.payment.domain.FailureReason;
import ch.alpian.payment.domain.IdempotencyConflictException;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentRepository;
import ch.alpian.payment.domain.Receiver;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckIdempotencyTaskTest {

    private static final String KEY = "idempotency-key-1";
    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);

    @Test
    void unused_key_returns_empty() {
        PaymentRepository repo = mock(PaymentRepository.class);
        when(repo.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());

        var task = new CheckIdempotencyTask(repo);
        assertThat(task.execute(new CheckIdempotencyTask.Arguments(KEY, HASH))).isEmpty();
    }

    @Test
    void same_key_and_same_hash_returns_the_existing_payment() {
        PaymentRepository repo = mock(PaymentRepository.class);
        Payment existing = Payment.rejected(
                KEY,
                HASH,
                UUID.randomUUID(),
                Receiver.internal(UUID.randomUUID()),
                Money.of("10.00", "CHF"),
                FailureReason.INSUFFICIENT_FUNDS);
        when(repo.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

        var task = new CheckIdempotencyTask(repo);
        Optional<Payment> result = task.execute(new CheckIdempotencyTask.Arguments(KEY, HASH));

        assertThat(result).contains(existing);
    }

    @Test
    void same_key_different_hash_throws_idempotency_conflict() {
        PaymentRepository repo = mock(PaymentRepository.class);
        Payment existing = Payment.rejected(
                KEY,
                HASH,
                UUID.randomUUID(),
                Receiver.internal(UUID.randomUUID()),
                Money.of("10.00", "CHF"),
                FailureReason.INSUFFICIENT_FUNDS);
        when(repo.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

        var task = new CheckIdempotencyTask(repo);
        assertThatThrownBy(() -> task.execute(new CheckIdempotencyTask.Arguments(KEY, OTHER_HASH)))
                .isInstanceOf(IdempotencyConflictException.class);
    }
}
