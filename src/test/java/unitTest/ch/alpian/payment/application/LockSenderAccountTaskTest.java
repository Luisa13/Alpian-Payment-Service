package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.alpian.payment.application.tasks.LockSenderAccountTask;
import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.AccountType;
import ch.alpian.payment.domain.CurrencyMismatchException;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.UnknownAccountException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LockSenderAccountTaskTest {

    @Test
    void lock_sender_throws_on_unknown_or_currency_mismatch() {
        AccountRepository repo = mock(AccountRepository.class);
        UUID missing = UUID.randomUUID();
        UUID eurId = UUID.randomUUID();
        Account eurAccount = new Account(
                eurId, "Alice", AccountType.CUSTOMER, Money.of("10.00", "EUR"), Money.of("10.00", "EUR"), 0);
        when(repo.findByIdForUpdate(missing)).thenReturn(Optional.empty());
        when(repo.findByIdForUpdate(eurId)).thenReturn(Optional.of(eurAccount));

        var task = new LockSenderAccountTask(repo);
        assertThatThrownBy(() -> task.execute(new LockSenderAccountTask.Arguments(missing, "CHF")))
                .isInstanceOf(UnknownAccountException.class);
        assertThatThrownBy(() -> task.execute(new LockSenderAccountTask.Arguments(eurId, "CHF")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void lock_sender_returns_the_account_when_found_with_matching_currency() {
        AccountRepository repo = mock(AccountRepository.class);
        UUID senderId = UUID.randomUUID();
        Account sender = new Account(
                senderId, "Alice", AccountType.CUSTOMER, Money.of("100.00", "CHF"), Money.of("100.00", "CHF"), 0);
        when(repo.findByIdForUpdate(senderId)).thenReturn(Optional.of(sender));

        var task = new LockSenderAccountTask(repo);
        Account result = task.execute(new LockSenderAccountTask.Arguments(senderId, "CHF"));

        assertThat(result).isEqualTo(sender);
    }
}
