package ch.example.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.example.template.application.flows.CreatePaymentFlow;
import ch.example.template.application.flows.CreatePaymentFlow.PaymentResult;
import ch.example.template.application.flows.GetPaymentFlow;
import ch.example.template.application.tasks.CheckIdempotencyTask;
import ch.example.template.application.tasks.FindPaymentByIdTask;
import ch.example.template.application.tasks.SavePaymentTask;
import ch.example.template.application.tasks.StoreIdempotencyResponseTask;
import ch.example.template.application.tasks.WriteOutboxTask;
import ch.example.template.domain.EntityNotFoundException;
import ch.example.template.domain.Money;
import ch.example.template.domain.Payment;
import ch.example.template.domain.PaymentRepository;
import ch.example.template.infrastructure.persistence.IdempotencyStore;
import ch.example.template.infrastructure.persistence.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class PaymentFlowsTest {

    // infrastructure mocks
    private PaymentRepository paymentRepo;
    private OutboxRepository outboxRepo;
    private IdempotencyStore idempotency;

    // tasks (wired with real logic against mocked infra)
    private CheckIdempotencyTask checkIdempotencyTask;
    private SavePaymentTask savePaymentTask;
    private WriteOutboxTask writeOutboxTask;
    private StoreIdempotencyResponseTask storeIdempotencyResponseTask;
    private FindPaymentByIdTask findPaymentByIdTask;

    // flows under test
    private CreatePaymentFlow createPaymentFlow;
    private GetPaymentFlow getPaymentFlow;

    @BeforeEach
    void setUp() {
        paymentRepo = mock(PaymentRepository.class);
        outboxRepo  = mock(OutboxRepository.class);
        idempotency = mock(IdempotencyStore.class);

        checkIdempotencyTask       = new CheckIdempotencyTask(idempotency);
        savePaymentTask            = new SavePaymentTask(paymentRepo);
        writeOutboxTask            = new WriteOutboxTask(outboxRepo);
        storeIdempotencyResponseTask = new StoreIdempotencyResponseTask(idempotency);
        findPaymentByIdTask        = new FindPaymentByIdTask(paymentRepo);

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        createPaymentFlow = new CreatePaymentFlow(
                checkIdempotencyTask,
                savePaymentTask,
                writeOutboxTask,
                storeIdempotencyResponseTask,
                mapper,
                new SimpleMeterRegistry());

        getPaymentFlow = new GetPaymentFlow(findPaymentByIdTask);
    }

    // -------------------------------------------------------------------------
    // CreatePaymentFlow
    // -------------------------------------------------------------------------

    @Test
    void create_saves_payment_and_outbox_event() {
        when(idempotency.findExisting(anyString(), anyString())).thenReturn(Optional.empty());
        when(paymentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var args = new CreatePaymentFlow.Arguments(
                new Money(new BigDecimal("100.00"), "CHF"),
                UUID.randomUUID(), UUID.randomUUID(), "test",
                "key-1", "hash-1");

        PaymentResult result = createPaymentFlow.initiate(args);

        assertThat(result).isInstanceOf(PaymentResult.Created.class);
        verify(paymentRepo).save(any(Payment.class));
        verify(outboxRepo).save(any());
        verify(idempotency).storeResponse(eq("key-1"), eq("hash-1"), eq(201), anyString());
    }

    @Test
    void create_replays_existing_idempotency_response() {
        var stored = new IdempotencyStore.StoredResponse("hash-1", 201, "{\"id\":\"" + UUID.randomUUID() + "\"}");
        when(idempotency.findExisting("key-1", "hash-1")).thenReturn(Optional.of(stored));

        var args = new CreatePaymentFlow.Arguments(
                new Money(new BigDecimal("100.00"), "CHF"),
                UUID.randomUUID(), UUID.randomUUID(), "test",
                "key-1", "hash-1");

        PaymentResult result = createPaymentFlow.initiate(args);

        assertThat(result).isInstanceOf(PaymentResult.Replayed.class);
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void create_uses_mdc_correlation_id_when_present() {
        when(idempotency.findExisting(anyString(), anyString())).thenReturn(Optional.empty());
        when(paymentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MDC.put("correlationId", UUID.randomUUID().toString());
        try {
            var args = new CreatePaymentFlow.Arguments(
                    new Money(new BigDecimal("100.00"), "CHF"),
                    UUID.randomUUID(), UUID.randomUUID(), "test",
                    "key-2", "hash-2");
            assertThat(createPaymentFlow.initiate(args)).isInstanceOf(PaymentResult.Created.class);
        } finally {
            MDC.clear();
        }
    }

    // -------------------------------------------------------------------------
    // GetPaymentFlow / FindPaymentByIdTask
    // -------------------------------------------------------------------------

    @Test
    void findById_returns_payment_when_found() {
        var id = UUID.randomUUID();
        var payment = Payment.create(new Money(new BigDecimal("50.00"), "EUR"),
                UUID.randomUUID(), UUID.randomUUID(), "desc");
        when(paymentRepo.findById(id)).thenReturn(Optional.of(payment));

        assertThat(getPaymentFlow.initiate(new GetPaymentFlow.Arguments(id))).isSameAs(payment);
    }

    @Test
    void findById_throws_when_not_found() {
        var id = UUID.randomUUID();
        when(paymentRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getPaymentFlow.initiate(new GetPaymentFlow.Arguments(id)))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
