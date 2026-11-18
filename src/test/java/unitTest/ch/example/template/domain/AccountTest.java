package ch.example.template.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void create_sets_all_fields() {
        var balance = new Money(new BigDecimal("1000.00"), "CHF");
        var account = Account.create("Alice", balance);

        assertThat(account.getId()).isNotNull();
        assertThat(account.getOwner()).isEqualTo("Alice");
        assertThat(account.getBalance().amount()).isEqualByComparingTo("1000.00");
        assertThat(account.getBalance().currency()).isEqualTo("CHF");
        assertThat(account.getCreatedAt()).isNotNull();
    }
}
