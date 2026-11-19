package ch.alpian.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceiverTest {

    @Test
    void internal_receiver_has_an_account_and_no_iban() {
        UUID id = UUID.randomUUID();
        Receiver r = Receiver.internal(id);
        assertThat(r.type()).isEqualTo(ReceiverType.INTERNAL);
        assertThat(r.accountId()).isEqualTo(id);
        assertThat(r.iban()).isNull();
        assertThat(r.isInternal()).isTrue();
    }

    @Test
    void external_receiver_has_an_iban_and_no_account() {
        Receiver r = Receiver.external("CH9300762011623852957");
        assertThat(r.type()).isEqualTo(ReceiverType.EXTERNAL);
        assertThat(r.accountId()).isNull();
        assertThat(r.isInternal()).isFalse();
    }

    @Test
    void receiver_must_be_exactly_one_kind() {
        assertThatThrownBy(() -> new Receiver(ReceiverType.INTERNAL, UUID.randomUUID(), "CH93"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Receiver(ReceiverType.INTERNAL, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Receiver(ReceiverType.EXTERNAL, null, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Receiver(ReceiverType.EXTERNAL, UUID.randomUUID(), "CH93"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
