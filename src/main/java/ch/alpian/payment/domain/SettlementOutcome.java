package ch.alpian.payment.domain;

/**
 * What the rail answered.
 *
 * <ul>
 *   <li>{@code SETTLED}: money moved; {@code reference} is the rail's reference.
 *   <li>{@code REJECTED}: a definite no; release the hold.
 *   <li>{@code UNKNOWN}: timeout or error. The money may have moved, so never fail on this.
 * </ul>
 */
public record SettlementOutcome(Status status, String reference) {

    public enum Status {
        SETTLED,
        REJECTED,
        UNKNOWN
    }

    public static SettlementOutcome settled(String reference) {
        return new SettlementOutcome(Status.SETTLED, reference);
    }

    public static SettlementOutcome rejected() {
        return new SettlementOutcome(Status.REJECTED, null);
    }

    public static SettlementOutcome unknown() {
        return new SettlementOutcome(Status.UNKNOWN, null);
    }
}
