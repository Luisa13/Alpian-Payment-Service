package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.alpian.payment.application.tasks.LockAccountsTask;
import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.AccountType;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.UnknownAccountException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LockAccountsTaskTest {

    @Test
    void lock_accounts_throws_when_any_account_is_missing() {
        AccountRepository repo = mock(AccountRepository.class);
        UUID presentId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        Account present = new Account(
                presentId, "Alice", AccountType.CUSTOMER, Money.of("10.00", "CHF"), Money.of("10.00", "CHF"), 0);
        when(repo.findAllByIdForUpdate(any())).thenReturn(List.of(present));

        var task = new LockAccountsTask(repo);
        assertThatThrownBy(() -> task.execute(List.of(presentId, missingId)))
                .isInstanceOf(UnknownAccountException.class);
    }

    @Test
    void lock_accounts_returns_all_accounts_keyed_by_id_when_all_present() {
        AccountRepository repo = mock(AccountRepository.class);
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        Account a =
                new Account(idA, "Alice", AccountType.CUSTOMER, Money.of("10.00", "CHF"), Money.of("10.00", "CHF"), 0);
        Account b =
                new Account(idB, "Bob", AccountType.CUSTOMER, Money.of("20.00", "CHF"), Money.of("20.00", "CHF"), 0);
        when(repo.findAllByIdForUpdate(any())).thenReturn(List.of(a, b));

        var task = new LockAccountsTask(repo);
        Map<UUID, Account> result = task.execute(List.of(idA, idB));

        assertThat(result).containsEntry(idA, a).containsEntry(idB, b);
    }
}
