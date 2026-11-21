package ch.alpian.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentStatus;
import ch.alpian.payment.domain.Receiver;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcPaymentRepositoryTest {

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, Answers.RETURNS_SELF);
    private final JdbcPaymentRepository repository = new JdbcPaymentRepository(jdbc);

    @Test
    void applyTransition_binds_updatedAt_from_payment_instead_of_database_now() {
        Payment payment = Payment.reserved(
                "key-1",
                "h".repeat(64),
                UUID.randomUUID(),
                Receiver.internal(UUID.randomUUID()),
                Money.of("25.00", "CHF"));
        payment.complete("rail-ref-1");

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.update()).thenReturn(1);

        boolean transitioned = repository.applyTransition(payment, PaymentStatus.RESERVED);

        assertThat(transitioned).isTrue();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("updated_at = :updatedAt").doesNotContain("now()");

        verify(spec).param("updatedAt", Timestamp.from(payment.getUpdatedAt()));
        verify(spec).param("finalizedAt", Timestamp.from(payment.getFinalizedAt()));
    }

    @Test
    void applyTransition_returns_false_when_no_row_matches() {
        Payment payment = Payment.reserved(
                "key-2",
                "h".repeat(64),
                UUID.randomUUID(),
                Receiver.internal(UUID.randomUUID()),
                Money.of("10.00", "CHF"));
        payment.complete("rail-ref-2");

        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.update()).thenReturn(0);

        boolean transitioned = repository.applyTransition(payment, PaymentStatus.RESERVED);

        assertThat(transitioned).isFalse();
        verify(spec).param("updatedAt", Timestamp.from(payment.getUpdatedAt()));
    }
}
