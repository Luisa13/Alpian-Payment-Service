package ch.example.template.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.example.template.shared.domain.EntityNotFoundException;
import ch.example.template.shared.domain.Money;
import ch.example.template.shared.infrastructure.IdempotencyStore;
import ch.example.template.transfer.domain.Transfer;
import ch.example.template.transfer.domain.TransferRepository;
import ch.example.template.transfer.infrastructure.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TransferServiceTest {

    private TransferRepository transferRepo;
    private OutboxRepository outboxRepo;
    private IdempotencyStore idempotency;
    private TransferService service;

    @BeforeEach
    void setUp() {
        transferRepo = mock(TransferRepository.class);
        outboxRepo = mock(OutboxRepository.class);
        idempotency = mock(IdempotencyStore.class);
        service = new TransferService(
                transferRepo,
                outboxRepo,
                idempotency,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                new SimpleMeterRegistry());
    }

    @Test
    void create_saves_transfer_and_outbox_event() {
        when(idempotency.findExisting(anyString(), anyString())).thenReturn(Optional.empty());
        when(transferRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new TransferService.CreateTransferCommand(
                new Money(new BigDecimal("100.00"), "CHF"), UUID.randomUUID(), UUID.randomUUID(), "test");

        var result = service.create(cmd, "key-1", "hash-1");

        assertThat(result).isInstanceOf(TransferService.TransferResult.Created.class);
        verify(transferRepo).save(any(Transfer.class));
        verify(outboxRepo).save(any());
        verify(idempotency).storeResponse(eq("key-1"), eq("hash-1"), eq(201), anyString());
    }

    @Test
    void create_replays_existing_idempotency_response() {
        var stored = new IdempotencyStore.StoredResponse("hash-1", 201, "{\"id\":\"" + UUID.randomUUID() + "\"}");
        when(idempotency.findExisting("key-1", "hash-1")).thenReturn(Optional.of(stored));

        var cmd = new TransferService.CreateTransferCommand(
                new Money(new BigDecimal("100.00"), "CHF"), UUID.randomUUID(), UUID.randomUUID(), "test");

        var result = service.create(cmd, "key-1", "hash-1");

        assertThat(result).isInstanceOf(TransferService.TransferResult.Replayed.class);
        verify(transferRepo, never()).save(any());
    }

    @Test
    void findById_returns_transfer_when_found() {
        var id = UUID.randomUUID();
        var transfer = Transfer.create(
                new Money(new BigDecimal("50.00"), "EUR"), UUID.randomUUID(), UUID.randomUUID(), "desc");
        when(transferRepo.findById(id)).thenReturn(Optional.of(transfer));

        assertThat(service.findById(id)).isSameAs(transfer);
    }

    @Test
    void create_uses_mdc_correlation_id_when_present() {
        when(idempotency.findExisting(anyString(), anyString())).thenReturn(Optional.empty());
        when(transferRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MDC.put("correlationId", UUID.randomUUID().toString());
        try {
            var cmd = new TransferService.CreateTransferCommand(
                    new Money(new BigDecimal("100.00"), "CHF"), UUID.randomUUID(), UUID.randomUUID(), "test");
            var result = service.create(cmd, "key-2", "hash-2");
            assertThat(result).isInstanceOf(TransferService.TransferResult.Created.class);
        } finally {
            MDC.clear();
        }
    }

    @Test
    void findById_throws_when_not_found() {
        var id = UUID.randomUUID();
        when(transferRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(EntityNotFoundException.class);
    }
}
