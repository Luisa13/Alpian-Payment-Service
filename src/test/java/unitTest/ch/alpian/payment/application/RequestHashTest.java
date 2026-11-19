package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import ch.alpian.payment.application.support.RequestHash;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Receiver;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestHashTest {

    private static final UUID SENDER = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final Receiver RECEIVER = Receiver.internal(UUID.fromString("a0000000-0000-0000-0000-000000000002"));

    @Test
    void is_a_64_character_hex_sha256() {
        assertThat(RequestHash.of(SENDER, RECEIVER, Money.of("10.00", "CHF"))).matches("[0-9a-f]{64}");
    }

    @Test
    void same_meaning_hashes_the_same_whatever_the_amount_formatting() {
        String a = RequestHash.of(SENDER, RECEIVER, Money.of("10", "CHF"));
        String b = RequestHash.of(SENDER, RECEIVER, Money.of("10.00", "CHF"));
        String c = RequestHash.of(SENDER, RECEIVER, Money.of("0010.0", "CHF"));
        assertThat(a).isEqualTo(b).isEqualTo(c);
    }

    @Test
    void any_difference_in_meaning_changes_the_hash() {
        String base = RequestHash.of(SENDER, RECEIVER, Money.of("10.00", "CHF"));
        assertThat(RequestHash.of(SENDER, RECEIVER, Money.of("10.01", "CHF"))).isNotEqualTo(base);
        assertThat(RequestHash.of(SENDER, RECEIVER, Money.of("10.00", "EUR"))).isNotEqualTo(base);
        assertThat(RequestHash.of(UUID.randomUUID(), RECEIVER, Money.of("10.00", "CHF")))
                .isNotEqualTo(base);
        assertThat(RequestHash.of(SENDER, Receiver.internal(UUID.randomUUID()), Money.of("10.00", "CHF")))
                .isNotEqualTo(base);
        assertThat(RequestHash.of(SENDER, Receiver.external("CH9300762011623852957"), Money.of("10.00", "CHF")))
                .isNotEqualTo(base);
    }
}
