package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.alpian.payment.application.tasks.ValidateReceiverTask;
import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountRepository;
import ch.alpian.payment.domain.AccountType;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.domain.exception.CurrencyMismatchException;
import ch.alpian.payment.domain.exception.UnknownAccountException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValidateReceiverTaskTest {

    @Test
    void validate_receiver_throws_on_unknown_or_currency_mismatch() {
        AccountRepository repo = mock(AccountRepository.class);
        UUID missing = UUID.randomUUID();
        UUID eurId = UUID.randomUUID();
        Account eurAccount =
                new Account(eurId, "Bob", AccountType.CUSTOMER, Money.of("0.00", "EUR"), Money.of("0.00", "EUR"), 0);
        when(repo.findById(missing)).thenReturn(Optional.empty());
        when(repo.findById(eurId)).thenReturn(Optional.of(eurAccount));

        var task = new ValidateReceiverTask(repo);
        assertThatThrownBy(() -> task.execute(new ValidateReceiverTask.Arguments(Receiver.internal(missing), "CHF")))
                .isInstanceOf(UnknownAccountException.class);
        assertThatThrownBy(() -> task.execute(new ValidateReceiverTask.Arguments(Receiver.internal(eurId), "CHF")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void validate_receiver_passes_for_external_receiver_without_db_lookup() {
        AccountRepository repo = mock(AccountRepository.class);
        var task = new ValidateReceiverTask(repo);

        assertThatCode(() -> task.execute(
                        new ValidateReceiverTask.Arguments(Receiver.external("CH9300762011623852957"), "CHF")))
                .doesNotThrowAnyException();
        verify(repo, never()).findById(any());
    }
}
