package ch.alpian.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PaymentStatusTest {

    private static final Set<PaymentStatus> FINAL_STATES =
            Set.of(PaymentStatus.REJECTED, PaymentStatus.COMPLETED, PaymentStatus.FAILED);

    @Test
    void reserved_can_become_completed() {
        assertThat(PaymentStatus.RESERVED.canTransitionTo(PaymentStatus.COMPLETED))
                .isTrue();
    }

    @Test
    void reserved_can_become_failed() {
        assertThat(PaymentStatus.RESERVED.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void reserved_cannot_stay_reserved_or_become_rejected(PaymentStatus next) {
        boolean allowed = next == PaymentStatus.COMPLETED || next == PaymentStatus.FAILED;
        assertThat(PaymentStatus.RESERVED.canTransitionTo(next)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void final_states_never_change(PaymentStatus next) {
        for (PaymentStatus from : FINAL_STATES) {
            assertThat(from.canTransitionTo(next))
                    .as("%s -> %s", from, next)
                    .isFalse();
        }
    }

    @Test
    void only_reserved_is_not_final() {
        for (PaymentStatus status : PaymentStatus.values()) {
            assertThat(status.isFinal()).isEqualTo(status != PaymentStatus.RESERVED);
        }
    }

    @Test
    void a_new_payment_starts_rejected_or_reserved_only() {
        assertThat(PaymentStatus.REJECTED.canStartAs()).isTrue();
        assertThat(PaymentStatus.RESERVED.canStartAs()).isTrue();
        assertThat(PaymentStatus.COMPLETED.canStartAs()).isFalse();
        assertThat(PaymentStatus.FAILED.canStartAs()).isFalse();
    }
}
