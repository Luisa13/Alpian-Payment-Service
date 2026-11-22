package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import ch.alpian.payment.application.support.HmacSignature;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HmacSignatureTest {

    @Test
    void sign_and_verify_round_trip_and_reject_tampered_or_blank_signatures() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String sig = HmacSignature.sign("secret", eventId, paymentId, "SETTLED", "REF-1");

        assertThat(HmacSignature.verify("secret", sig, eventId, paymentId, "SETTLED", "REF-1"))
                .isTrue();
        assertThat(HmacSignature.verify("wrong", sig, eventId, paymentId, "SETTLED", "REF-1"))
                .isFalse();
        assertThat(HmacSignature.verify("secret", sig, eventId, paymentId, "REJECTED", null))
                .isFalse();
        assertThat(HmacSignature.verify("secret", null, eventId, paymentId, "SETTLED", "REF-1"))
                .isFalse();
        assertThat(HmacSignature.verify("secret", "  ", eventId, paymentId, "SETTLED", "REF-1"))
                .isFalse();
    }
}
