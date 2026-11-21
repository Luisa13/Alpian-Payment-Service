package ch.alpian.payment.domain;

/**
 * Port: the settlement rail. The payment id is the rail's idempotency key.
 *
 * <ul>
 *   <li>{@link #settle} submits the payment for processing.
 *   <li>{@link #getStatus} queries the outcome of a previously submitted payment without
 *       re-submitting it. Recovery uses this to check whether a timed-out submission actually went
 *       through before deciding to retry.
 * </ul>
 */
public interface SettlementRail {

    SettlementOutcome settle(SettlementRequest request);

    /**
     * Returns the known outcome for a previously submitted payment.
     *
     * <ul>
     *   <li>{@code SETTLED} / {@code REJECTED}: definitive answer; use it to finalize.
     *   <li>{@code UNKNOWN}: the rail has no record (never received the submission, or a timeout
     *       prevented storage); safe to re-submit via {@link #settle}.
     * </ul>
     */
    SettlementOutcome getStatus(SettlementRequest request);
}
