package ch.alpian.payment.infrastructure.rail;

import static org.assertj.core.api.Assertions.assertThat;

import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.SettlementRail;
import ch.alpian.payment.domain.SettlementRequest;
import ch.alpian.payment.integration.PaymentIntegrationTest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T7: rail port routing and persistent idempotency in rail_sim_transfers. */
class SettlementRailIT extends PaymentIntegrationTest {

    private static final String IBAN = "CH9300762011623852957";

    @Autowired
    SettlementRail rail;

    @Test
    void internal_transfer_always_settles_without_touching_rail_sim_transfers() {
        UUID paymentId = UUID.randomUUID();
        var req = new SettlementRequest(
                paymentId, UUID.randomUUID(), Receiver.internal(UUID.randomUUID()), Money.of("25.00", "CHF"));

        SettlementOutcome outcome = rail.settle(req);

        assertThat(outcome.status()).isEqualTo(SettlementOutcome.Status.SETTLED);
        assertThat(outcome.reference()).isEqualTo("INT-" + paymentId);
        assertThat(count("SELECT COUNT(*) FROM rail_sim_transfers WHERE payment_id = :id", paymentId))
                .isZero();
    }

    @Test
    void external_transfer_persists_outcome_and_returns_the_same_outcome_across_restarts() {
        UUID paymentId = UUID.randomUUID();
        var req =
                new SettlementRequest(paymentId, UUID.randomUUID(), Receiver.external(IBAN), Money.of("50.00", "CHF"));

        // First instance settles and persists to rail_sim_transfers
        var firstInstance = new SimulatedExternalRail(
                jdbc, new SimulatedRailProperties(Duration.ZERO, 0.0, 0.0), new SecureRandom());
        SettlementOutcome first = firstInstance.settle(req);
        assertThat(first.status()).isEqualTo(SettlementOutcome.Status.SETTLED);
        assertThat(first.reference()).startsWith("SIM-");

        // Second instance with 100% rejection & 100% timeout (simulating a restart with different config)
        // must still return the persisted SETTLED outcome for the same payment id.
        var restartedInstance = new SimulatedExternalRail(
                jdbc, new SimulatedRailProperties(Duration.ZERO, 1.0, 1.0), new SecureRandom());
        SettlementOutcome second = restartedInstance.settle(req);
        assertThat(second).isEqualTo(first);

        // And the wired Spring bean returns the exact same outcome.
        assertThat(rail.settle(req)).isEqualTo(first);
    }

    @Test
    void external_transfer_remembers_rejection_and_does_not_persist_timeout() {
        UUID rejectedPayment = UUID.randomUUID();
        var rejectRail = new SimulatedExternalRail(
                jdbc, new SimulatedRailProperties(Duration.ZERO, 1.0, 0.0), new SecureRandom());
        var rejectReq = new SettlementRequest(
                rejectedPayment, UUID.randomUUID(), Receiver.external(IBAN), Money.of("10.00", "CHF"));

        assertThat(rejectRail.settle(rejectReq).status()).isEqualTo(SettlementOutcome.Status.REJECTED);
        // Even if rejectionRate drops to 0 after restart, the stored REJECTED outcome is returned.
        assertThat(rail.settle(rejectReq).status()).isEqualTo(SettlementOutcome.Status.REJECTED);

        UUID timedOutPayment = UUID.randomUUID();
        var timeoutRail = new SimulatedExternalRail(
                jdbc, new SimulatedRailProperties(Duration.ZERO, 0.0, 1.0), new SecureRandom());
        var timeoutReq = new SettlementRequest(
                timedOutPayment, UUID.randomUUID(), Receiver.external(IBAN), Money.of("10.00", "CHF"));

        assertThat(timeoutRail.settle(timeoutReq).status()).isEqualTo(SettlementOutcome.Status.UNKNOWN);
        assertThat(count("SELECT COUNT(*) FROM rail_sim_transfers WHERE payment_id = :id", timedOutPayment))
                .isZero();
    }
}
