package ch.alpian.payment.domain;

import java.util.UUID;

/** Port: audit trail and dedup store for external rail callbacks. */
public interface RailCallbackRepository {

    /**
     * Records a callback event if {@code eventId} has not been seen before.
     *
     * @return {@code true} if recorded, {@code false} if {@code eventId} already existed
     */
    boolean record(UUID eventId, UUID paymentId, String outcome);
}
