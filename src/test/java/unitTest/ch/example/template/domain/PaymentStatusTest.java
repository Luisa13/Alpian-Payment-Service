package ch.example.template.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PaymentStatusTest {

    @Test
    void requested_code_is_REQUESTED() {
        assertThat(new PaymentStatus.Requested().code()).isEqualTo("REQUESTED");
    }

    @Test
    void settled_code_is_SETTLED() {
        assertThat(new PaymentStatus.Settled().code()).isEqualTo("SETTLED");
    }

    @Test
    void failed_code_is_FAILED() {
        assertThat(new PaymentStatus.Failed("insufficient funds").code()).isEqualTo("FAILED");
    }

    @Test
    void failed_requires_non_blank_reason() {
        assertThatThrownBy(() -> new PaymentStatus.Failed("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentStatus.Failed(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromCode_requested() {
        assertThat(PaymentStatus.fromCode("REQUESTED", null)).isInstanceOf(PaymentStatus.Requested.class);
    }

    @Test
    void fromCode_settled() {
        assertThat(PaymentStatus.fromCode("SETTLED", null)).isInstanceOf(PaymentStatus.Settled.class);
    }

    @Test
    void fromCode_failed_with_reason() {
        var status = PaymentStatus.fromCode("FAILED", "timeout");
        assertThat(status).isInstanceOf(PaymentStatus.Failed.class);
        assertThat(((PaymentStatus.Failed) status).reason()).isEqualTo("timeout");
    }

    @Test
    void fromCode_unknown_throws() {
        assertThatThrownBy(() -> PaymentStatus.fromCode("UNKNOWN", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
