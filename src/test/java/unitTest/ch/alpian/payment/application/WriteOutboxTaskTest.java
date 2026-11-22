package ch.alpian.payment.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.alpian.payment.application.events.PaymentEvents;
import ch.alpian.payment.application.tasks.WriteOutboxTask;
import ch.alpian.payment.domain.FailureReason;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.OutboxRepository;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.Receiver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WriteOutboxTaskTest {

    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final WriteOutboxTask task =
            new WriteOutboxTask(outbox, new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void reserved_payment_appends_outbox_row_with_correct_event_type_and_aggregate_id() {
        Payment payment = Payment.reserved(
                "key-1",
                "h".repeat(64),
                UUID.randomUUID(),
                Receiver.internal(UUID.randomUUID()),
                Money.of("25.00", "CHF"));

        task.execute(payment);

        verify(outbox).append(any(), eq(payment.getId()), eq(PaymentEvents.PAYMENT_RESERVED), any());
    }

    @Test
    void rejected_payment_appends_outbox_row_with_correct_event_type_and_aggregate_id() {
        Payment payment = Payment.rejected(
                "key-2",
                "h".repeat(64),
                UUID.randomUUID(),
                Receiver.internal(UUID.randomUUID()),
                Money.of("25.00", "CHF"),
                FailureReason.INSUFFICIENT_FUNDS);

        task.execute(payment);

        verify(outbox).append(any(), eq(payment.getId()), eq(PaymentEvents.PAYMENT_REJECTED), any());
    }
}
