package ch.alpian.payment.domain;

/** Port: the settlement rail. The payment id is the rail's idempotency key. */
public interface SettlementRail {

    SettlementOutcome settle(SettlementRequest request);
}
