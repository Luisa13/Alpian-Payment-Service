package ch.alpian.payment.domain;

import java.util.UUID;

/** Port: double-entry ledger. A payment posts at most once per account. */
public interface LedgerRepository {

    void postDebit(UUID paymentId, UUID accountId, Money amount);

    void postCredit(UUID paymentId, UUID accountId, Money amount);
}
