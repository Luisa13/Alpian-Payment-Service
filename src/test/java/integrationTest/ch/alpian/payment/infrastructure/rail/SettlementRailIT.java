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
    void external_transfer_returns_accepted_and_pending_until_delivered_across_restarts() {
        UUID paymentId = UUID.randomUUID();
        var req =
                new SettlementRequest(paymentId, UUID.randomUUID(), Receiver.external(IBAN), Money.of("50.00", "CHF"));

        var firstInstance = new SimulatedExternalRail(
                jdbc, new SimulatedRailProperties(Duration.ZERO, Duration.ofMillis(50), 0.0, 0.0), new SecureRandom());
        SettlementOutcome first = firstInstance.settle(req);
        assertThat(first.status()).isEqualTo(SettlementOutcome.Status.ACCEPTED);
        assertThat(firstInstance.getStatus(req).status()).isEqualTo(SettlementOutcome.Status.PENDING);

        // Second instance with 100% rejection & 100% timeout (simulating a restart with different config)
        // must still return ACCEPTED on settle() and PENDING on getStatus() before delivery.
        var restartedInstance = new SimulatedExternalRail(
                jdbc, new SimulatedRailProperties(Duration.ZERO, Duration.ofMillis(50), 1.0, 1.0), new SecureRandom());
        assertThat(restartedInstance.settle(req).status()).isEqualTo(SettlementOutcome.Status.ACCEPTED);
        assertThat(restartedInstance.getStatus(req).status()).isEqualTo(SettlementOutcome.Status.PENDING);
        assertThat(rail.getStatus(req).status()).isEqualTo(SettlementOutcome.Status.PENDING);
    }

    @Test
    void external_transfer_returns_stored_outcome_after_delivery_and_does_not_persist_timeout() {
        UUID settledPayment = UUID.randomUUID();
        var settledReq = new SettlementRequest(
                settledPayment, UUID.randomUUID(), Receiver.external(IBAN), Money.of("10.00", "CHF"));
        assertThat(rail.settle(settledReq).status()).isEqualTo(SettlementOutcome.Status.ACCEPTED);
        assertThat(rail.getStatus(settledReq).status()).isEqualTo(SettlementOutcome.Status.PENDING);

        jdbc.sql("""
                        UPDATE rail_sim_transfers
                        SET outcome = 'SETTLED', reference = 'SIM-12345678', delivered_at = now()
                        WHERE payment_id = :id
                        """).param("id", settledPayment).update();

        SettlementOutcome delivered = rail.getStatus(settledReq);
        assertThat(delivered).isEqualTo(SettlementOutcome.settled("SIM-12345678"));
        assertThat(rail.settle(settledReq)).isEqualTo(delivered);

        UUID rejectedPayment = UUID.randomUUID();
        var rejectedReq = new SettlementRequest(
                rejectedPayment, UUID.randomUUID(), Receiver.external(IBAN), Money.of("10.00", "CHF"));
        assertThat(rail.settle(rejectedReq).status()).isEqualTo(SettlementOutcome.Status.ACCEPTED);
        jdbc.sql("""
                        UPDATE rail_sim_transfers
                        SET outcome = 'REJECTED', reference = 'SIM-REJ', delivered_at = now()
                        WHERE payment_id = :id
                        """).param("id", rejectedPayment).update();
        assertThat(rail.getStatus(rejectedReq).status()).isEqualTo(SettlementOutcome.Status.REJECTED);

        UUID timedOutPayment = UUID.randomUUID();
        var timeoutRail = new SimulatedExternalRail(
                jdbc, new SimulatedRailProperties(Duration.ZERO, Duration.ofMillis(50), 0.0, 1.0), new SecureRandom());
        var timeoutReq = new SettlementRequest(
                timedOutPayment, UUID.randomUUID(), Receiver.external(IBAN), Money.of("10.00", "CHF"));

        assertThat(timeoutRail.settle(timeoutReq).status()).isEqualTo(SettlementOutcome.Status.UNKNOWN);
        assertThat(timeoutRail.getStatus(timeoutReq).status()).isEqualTo(SettlementOutcome.Status.UNKNOWN);
        assertThat(count("SELECT COUNT(*) FROM rail_sim_transfers WHERE payment_id = :id", timedOutPayment))
                .isZero();
    }
}
