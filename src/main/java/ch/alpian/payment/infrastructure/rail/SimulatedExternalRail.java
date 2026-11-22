package ch.alpian.payment.infrastructure.rail;

import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.SettlementRail;
import ch.alpian.payment.domain.SettlementRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Simulated external rail (receiver is an IBAN).
 *
 * <p>Idempotent per payment id: accepting a submission inserts a pending row into
 * {@code rail_sim_transfers} with {@code deliver_at} and returns {@code ACCEPTED}. The outcome
 * decision and delivery happen asynchronously in the dispatcher. A simulated timeout answers
 * {@code UNKNOWN} and stores nothing, so a retry gets a fresh roll.
 */
class SimulatedExternalRail implements SettlementRail {

    private final JdbcClient jdbc;
    private final SimulatedRailProperties properties;
    private final RandomGenerator random;

    SimulatedExternalRail(JdbcClient jdbc, SimulatedRailProperties properties, RandomGenerator random) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.random = random;
    }

    @Override
    public SettlementOutcome settle(SettlementRequest request) {
        Optional<SettlementOutcome> known = findStored(request.paymentId(), true);
        if (known.isPresent()) {
            return known.get();
        }

        pause();
        if (random.nextDouble() < properties.timeoutRate()) {
            return SettlementOutcome.unknown();
        }

        Instant deliverAt = Instant.now().plus(properties.deliveryDelay());
        jdbc.sql("""
                        INSERT INTO rail_sim_transfers (payment_id, deliver_at)
                        VALUES (:paymentId, :deliverAt)
                        ON CONFLICT (payment_id) DO NOTHING
                        """)
                .param("paymentId", request.paymentId())
                .param("deliverAt", Timestamp.from(deliverAt))
                .update();

        return findStored(request.paymentId(), true)
                .orElseThrow(() -> new IllegalStateException("Rail submission vanished for " + request.paymentId()));
    }

    @Override
    public SettlementOutcome getStatus(SettlementRequest request) {
        return findStored(request.paymentId(), false).orElse(SettlementOutcome.unknown());
    }

    private Optional<SettlementOutcome> findStored(UUID paymentId, boolean forSettle) {
        return jdbc.sql("SELECT outcome, reference, delivered_at FROM rail_sim_transfers WHERE payment_id = :id")
                .param("id", paymentId)
                .query((rs, n) -> {
                    if (rs.getTimestamp("delivered_at") == null) {
                        return forSettle ? SettlementOutcome.accepted() : SettlementOutcome.pending();
                    }
                    return "SETTLED".equals(rs.getString("outcome"))
                            ? SettlementOutcome.settled(rs.getString("reference"))
                            : SettlementOutcome.rejected();
                })
                .optional();
    }

    private void pause() {
        if (properties.delay().isZero()) {
            return;
        }
        try {
            Thread.sleep(properties.delay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during the simulated rail call", e);
        }
    }
}
