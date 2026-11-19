package ch.alpian.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static Money chf(String amount) {
        return Money.of(amount, "CHF");
    }

    private static Account account(String balance, String available) {
        return new Account(UUID.randomUUID(), "Alice", AccountType.CUSTOMER, chf(balance), chf(available), 0);
    }

    @Test
    void hold_reduces_available_balance_only() {
        Account a = account("100.00", "100.00");
        a.hold(chf("30.00"));
        assertThat(a.getBalance()).isEqualTo(chf("100.00"));
        assertThat(a.getAvailableBalance()).isEqualTo(chf("70.00"));
        assertThat(a.held()).isEqualTo(chf("30.00"));
    }

    @Test
    void hold_of_exactly_the_available_balance_is_allowed() {
        Account a = account("100.00", "100.00");
        a.hold(chf("100.00"));
        assertThat(a.getAvailableBalance().isZero()).isTrue();
    }

    @Test
    void hold_above_available_balance_fails_and_changes_nothing() {
        Account a = account("100.00", "40.00");
        assertThatThrownBy(() -> a.hold(chf("40.01"))).isInstanceOf(InsufficientFundsException.class);
        assertThat(a.getAvailableBalance()).isEqualTo(chf("40.00"));
        assertThat(a.getBalance()).isEqualTo(chf("100.00"));
    }

    @Test
    void release_gives_the_hold_back() {
        Account a = account("100.00", "100.00");
        a.hold(chf("30.00"));
        a.release(chf("30.00"));
        assertThat(a.getAvailableBalance()).isEqualTo(chf("100.00"));
    }

    @Test
    void release_more_than_held_fails() {
        Account a = account("100.00", "90.00");
        assertThatThrownBy(() -> a.release(chf("10.01"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void settle_debit_turns_the_hold_into_a_debit() {
        Account a = account("100.00", "100.00");
        a.hold(chf("30.00"));
        a.settleDebit(chf("30.00"));
        assertThat(a.getBalance()).isEqualTo(chf("70.00"));
        assertThat(a.getAvailableBalance()).isEqualTo(chf("70.00"));
        assertThat(a.held().isZero()).isTrue();
    }

    @Test
    void settle_debit_without_a_matching_hold_fails() {
        Account a = account("100.00", "100.00");
        assertThatThrownBy(() -> a.settleDebit(chf("1.00"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void credit_raises_both_balances() {
        Account a = account("100.00", "80.00");
        a.credit(chf("25.00"));
        assertThat(a.getBalance()).isEqualTo(chf("125.00"));
        assertThat(a.getAvailableBalance()).isEqualTo(chf("105.00"));
    }

    @Test
    void amounts_must_be_positive() {
        Account a = account("100.00", "100.00");
        Money zero = Money.zero("CHF");
        assertThatThrownBy(() -> a.hold(zero)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.release(zero)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.settleDebit(zero)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.credit(zero)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void other_currency_is_refused() {
        Account a = account("100.00", "100.00");
        assertThatThrownBy(() -> a.hold(Money.of("1.00", "EUR"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void available_balance_can_never_exceed_balance() {
        assertThatThrownBy(() -> account("10.00", "10.01")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hold_then_settle_then_credit_keeps_money_conserved_between_two_accounts() {
        Account sender = account("100.00", "100.00");
        Account receiver = account("50.00", "50.00");
        sender.hold(chf("40.00"));
        sender.settleDebit(chf("40.00"));
        receiver.credit(chf("40.00"));
        assertThat(sender.getBalance().add(receiver.getBalance())).isEqualTo(chf("150.00"));
    }
}
