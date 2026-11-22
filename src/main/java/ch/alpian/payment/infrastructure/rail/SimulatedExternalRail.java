package ch.alpian.payment.infrastructure.rail;

import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.SettlementRail;
import ch.alpian.payment.domain.SettlementRequest;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Simulated external rail (receiver is an IBAN).
 *
 * <p>Idempotent per payment id: the first definite answer (SETTLED or REJECTED) is stored in
 * {@code rail_sim_transfers} and returned for every later call, also after a restart. A simulated
 * timeout answers UNKNOWN and stores nothing, so a retry gets a fresh roll.
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
        Optional<SettlementOutcome> known = findStored(request.paymentId());
        if (known.isPresent()) {
            return known.get();
        }

        pause();
        if (random.nextDouble() < properties.timeoutRate()) {
            return SettlementOutcome.unknown();
        }

        String outcome = random.nextDouble() < properties.rejectionRate() ? "REJECTED" : "SETTLED";
        jdbc.sql("""
                        INSERT INTO rail_sim_transfers (payment_id, outcome, reference)
                        VALUES (:paymentId, :outcome, :reference)
                        ON CONFLICT (payment_id) DO NOTHING
                        """)
                .param("paymentId", request.paymentId())
                .param("outcome", outcome)
                .param("reference", "SIM-" + UUID.randomUUID().toString().substring(0, 8))
                .update();

        // Re-read: if a concurrent call stored an answer first, that answer wins for everyone.
        return findStored(request.paymentId())
                .orElseThrow(() -> new IllegalStateException("Rail outcome vanished for " + request.paymentId()));
    }

    @Override
    public SettlementOutcome getStatus(SettlementRequest request) {
        // Return the stored outcome if the rail reached a decision; UNKNOWN otherwise.
        // No network call, no side effects.
        return findStored(request.paymentId()).orElse(SettlementOutcome.unknown());
    }

    private Optional<SettlementOutcome> findStored(UUID paymentId) {
        return jdbc.sql("SELECT outcome, reference FROM rail_sim_transfers WHERE payment_id = :id")
                .param("id", paymentId)
                .query((rs, n) -> "SETTLED".equals(rs.getString("outcome"))
                        ? SettlementOutcome.settled(rs.getString("reference"))
                        : SettlementOutcome.rejected())
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
