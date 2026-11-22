package ch.alpian.payment.infrastructure.rail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.alpian.payment.application.flows.CreatePaymentFlow;
import ch.alpian.payment.application.flows.PaymentResult;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentRepository;
import ch.alpian.payment.domain.PaymentStatus;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.integration.PaymentIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SimulatedCallbackDispatcherIT extends PaymentIntegrationTest {

    private static final String IBAN = "CH9300762011623852957";

    @Autowired
    CreatePaymentFlow createPaymentFlow;

    @Autowired
    PaymentRepository payments;

    @Test
    void external_payment_is_submitted_and_then_completed_asynchronously_by_dispatcher() {
        UUID sender = newAccount("200.00");

        PaymentResult result = createPaymentFlow.initiate(new CreatePaymentFlow.Arguments(
                "disp-" + UUID.randomUUID(), sender, Receiver.external(IBAN), Money.of("75.00", "CHF")));

        assertThat(result.kind()).isEqualTo(PaymentResult.Kind.ACCEPTED);
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.SUBMITTED);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Payment finalized = payments.findById(result.payment().getId()).orElseThrow();
            assertThat(finalized.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(finalized.getRailReference()).startsWith("SIM-");
        });
    }
}
