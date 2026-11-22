package ch.alpian.payment.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Payment lifecycle.
 *
 * <pre>
 *   new ──▶ REJECTED                              (final, nothing held)
 *   new ──▶ RESERVED ──▶ SUBMITTED ──▶ COMPLETED  (final)
 *               │                 └──▶ FAILED     (final, hold released)
 *               ├────────────────────▶ COMPLETED  (final, sync rail or early callback)
 *               └────────────────────▶ FAILED     (final, sync rail or early callback)
 * </pre>
 *
 * Final states never change.
 */
public enum PaymentStatus {
    REJECTED,
    RESERVED,
    SUBMITTED,
    COMPLETED,
    FAILED;

    /** States a payment may be created in. */
    private static final Set<PaymentStatus> INITIAL = EnumSet.of(REJECTED, RESERVED);

    public boolean isFinal() {
        return this != RESERVED && this != SUBMITTED;
    }

    public boolean canStartAs() {
        return INITIAL.contains(this);
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case RESERVED -> next == SUBMITTED || next == COMPLETED || next == FAILED;
            case SUBMITTED -> next == COMPLETED || next == FAILED;
            case REJECTED, COMPLETED, FAILED -> false;
        };
    }
}
