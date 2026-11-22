package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.alpian.payment.application.support.RailCallbackProperties;
import org.junit.jupiter.api.Test;

class RailCallbackPropertiesTest {

    @Test
    void rejects_null_or_blank_secret_and_redacts_toString() {
        assertThatThrownBy(() -> new RailCallbackProperties(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RailCallbackProperties("  ")).isInstanceOf(IllegalArgumentException.class);

        var props = new RailCallbackProperties("super-secret-value");
        assertThat(props.hmacSecret()).isEqualTo("super-secret-value");
        assertThat(props.toString()).doesNotContain("super-secret-value");
    }
}
