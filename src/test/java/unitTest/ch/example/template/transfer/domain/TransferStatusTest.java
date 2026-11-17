package ch.example.template.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TransferStatusTest {

    @Test
    void requested_code_is_REQUESTED() {
        assertThat(new TransferStatus.Requested().code()).isEqualTo("REQUESTED");
    }

    @Test
    void settled_code_is_SETTLED() {
        assertThat(new TransferStatus.Settled().code()).isEqualTo("SETTLED");
    }

    @Test
    void failed_code_is_FAILED() {
        assertThat(new TransferStatus.Failed("insufficient funds").code()).isEqualTo("FAILED");
    }

    @Test
    void failed_requires_non_blank_reason() {
        assertThatThrownBy(() -> new TransferStatus.Failed("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransferStatus.Failed(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromCode_requested() {
        assertThat(TransferStatus.fromCode("REQUESTED", null)).isInstanceOf(TransferStatus.Requested.class);
    }

    @Test
    void fromCode_settled() {
        assertThat(TransferStatus.fromCode("SETTLED", null)).isInstanceOf(TransferStatus.Settled.class);
    }

    @Test
    void fromCode_failed_with_reason() {
        var status = TransferStatus.fromCode("FAILED", "timeout");
        assertThat(status).isInstanceOf(TransferStatus.Failed.class);
        assertThat(((TransferStatus.Failed) status).reason()).isEqualTo("timeout");
    }

    @Test
    void fromCode_unknown_throws() {
        assertThatThrownBy(() -> TransferStatus.fromCode("UNKNOWN", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
