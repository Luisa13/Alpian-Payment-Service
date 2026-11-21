package ch.alpian.payment.infrastructure.rail;

import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.SettlementRail;
import ch.alpian.payment.domain.SettlementRequest;

/** Receiver is one of our accounts: always settles; the credit happens in the finalizer. */
class InternalTransferRail implements SettlementRail {

    @Override
    public SettlementOutcome settle(SettlementRequest request) {
        return SettlementOutcome.settled("INT-" + request.paymentId());
    }

    @Override
    public SettlementOutcome getStatus(SettlementRequest request) {
        // Internal transfers always settle synchronously; if one is still RESERVED it means
        // Tx2 failed before it could record COMPLETED. The settlement itself always succeeds,
        // so the status is always SETTLED.
        return SettlementOutcome.settled("INT-" + request.paymentId());
    }
}
