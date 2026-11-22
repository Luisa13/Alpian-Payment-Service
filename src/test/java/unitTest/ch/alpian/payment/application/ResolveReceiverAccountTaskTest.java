package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.alpian.payment.application.tasks.ResolveReceiverAccountTask;
import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.Receiver;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResolveReceiverAccountTaskTest {

    @Test
    void internal_receiver_returns_receiver_account_id() {
        AccountRepository repo = mock(AccountRepository.class);
        UUID receiverId = UUID.randomUUID();
        Payment payment = Payment.reserved(
                "k1", "h1", UUID.randomUUID(), Receiver.internal(receiverId), Money.of("10.00", "CHF"));

        UUID resolved = new ResolveReceiverAccountTask(repo).execute(payment);

        assertThat(resolved).isEqualTo(receiverId);
        verify(repo, never()).findClearingAccountIds(anyString());
    }

    @Test
    void external_receiver_selects_clearing_shard_deterministically_and_distributes_across_shards() {
        AccountRepository repo = mock(AccountRepository.class);
        List<UUID> shards = List.of(
                UUID.fromString("f0000000-0000-0000-0000-000000000001"),
                UUID.fromString("f0000000-0000-0000-0000-000000000002"),
                UUID.fromString("f0000000-0000-0000-0000-000000000003"),
                UUID.fromString("f0000000-0000-0000-0000-000000000004"));
        when(repo.findClearingAccountIds("CHF")).thenReturn(shards);
        var task = new ResolveReceiverAccountTask(repo);

        Payment sample = Payment.reserved(
                "k1", "h1", UUID.randomUUID(), Receiver.external("CH9300762011623852957"), Money.of("10.00", "CHF"));
        UUID first = task.execute(sample);
        UUID second = task.execute(sample);
        assertThat(first).isIn(shards).isEqualTo(second);

        Set<UUID> chosen = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            Payment p = Payment.reserved(
                    "k-" + i,
                    "h-" + i,
                    UUID.randomUUID(),
                    Receiver.external("CH9300762011623852957"),
                    Money.of("10.00", "CHF"));
            chosen.add(task.execute(p));
        }
        assertThat(chosen).containsAll(shards);
    }

    @Test
    void external_receiver_throws_when_no_clearing_accounts_configured() {
        AccountRepository repo = mock(AccountRepository.class);
        when(repo.findClearingAccountIds("EUR")).thenReturn(List.of());
        Payment payment = Payment.reserved(
                "k1", "h1", UUID.randomUUID(), Receiver.external("CH9300762011623852957"), Money.of("10.00", "EUR"));

        assertThatThrownBy(() -> new ResolveReceiverAccountTask(repo).execute(payment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EUR");
    }
}
