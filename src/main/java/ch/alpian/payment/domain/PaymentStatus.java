package ch.alpian.payment.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Payment lifecycle (Phase 1).
 *
 * <pre>
 *   new ──▶ REJECTED                      (final, nothing held)
 *   new ──▶ RESERVED ──▶ COMPLETED        (final)
 *                   └──▶ FAILED           (final, hold released)
 * </pre>
 *
 * Final states never change. {@code SETTLING} is added in Phase 2.
 */
public enum PaymentStatus {
    REJECTED,
    RESERVED,
    COMPLETED,
    FAILED;

    /** States a payment may be created in. */
    private static final Set<PaymentStatus> INITIAL = EnumSet.of(REJECTED, RESERVED);

    public boolean isFinal() {
        return this != RESERVED;
    }

    public boolean canStartAs() {
        return INITIAL.contains(this);
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return this == RESERVED && (next == COMPLETED || next == FAILED);
    }
}
