package ch.example.template.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.example.template.shared.domain.Money;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferTest {

    private Transfer newTransfer() {
        return Transfer.create(
                new Money(new BigDecimal("100.00"), "CHF"), UUID.randomUUID(), UUID.randomUUID(), "Rent");
    }

    @Test
    void created_transfer_is_in_requested_status() {
        var t = newTransfer();
        assertThat(t.getTransferStatus()).isInstanceOf(TransferStatus.Requested.class);
        assertThat(t.getId()).isNotNull();
        assertThat(t.getCreatedAt()).isNotNull();
        assertThat(t.getUpdatedAt()).isNotNull();
    }

    @Test
    void settle_transitions_to_settled() {
        var t = newTransfer();
        t.settle();
        assertThat(t.getTransferStatus()).isInstanceOf(TransferStatus.Settled.class);
    }

    @Test
    void fail_transitions_to_failed() {
        var t = newTransfer();
        t.fail("timeout");
        assertThat(t.getTransferStatus()).isInstanceOf(TransferStatus.Failed.class);
        assertThat(((TransferStatus.Failed) t.getTransferStatus()).reason()).isEqualTo("timeout");
    }

    @Test
    void cannot_settle_already_settled_transfer() {
        var t = newTransfer();
        t.settle();
        assertThatThrownBy(t::settle).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannot_fail_already_settled_transfer() {
        var t = newTransfer();
        t.settle();
        assertThatThrownBy(() -> t.fail("reason")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannot_settle_already_failed_transfer() {
        var t = newTransfer();
        t.fail("error");
        assertThatThrownBy(t::settle).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void get_amount_returns_money() {
        var amount = new Money(new BigDecimal("50.00"), "EUR");
        var t = Transfer.create(amount, UUID.randomUUID(), UUID.randomUUID(), "test");
        assertThat(t.getAmount().amount()).isEqualByComparingTo("50.00");
        assertThat(t.getAmount().currency()).isEqualTo("EUR");
    }

    @Test
    void version_is_null_on_new_entity() {
        // Spring Data JDBC assigns version on first save; in-memory the field is null
        assertThat(newTransfer().getVersion()).isNull();
    }
}
