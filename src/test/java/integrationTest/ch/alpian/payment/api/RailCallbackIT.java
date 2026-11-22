package ch.alpian.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.alpian.payment.application.flows.CreatePaymentFlow;
import ch.alpian.payment.application.flows.PaymentResult;
import ch.alpian.payment.application.support.HmacSignature;
import ch.alpian.payment.application.support.RailCallbackProperties;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentRepository;
import ch.alpian.payment.domain.PaymentStatus;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.integration.PaymentIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class RailCallbackIT extends PaymentIntegrationTest {

    private static final String IBAN = "CH9300762011623852957";

    @Autowired
    MockMvc mvc;

    @Autowired
    CreatePaymentFlow createPaymentFlow;

    @Autowired
    PaymentRepository payments;

    @Autowired
    RailCallbackProperties callbackProperties;

    private Payment createSubmittedExternalPayment() {
        UUID sender = newAccount("500.00");
        PaymentResult result = createPaymentFlow.initiate(new CreatePaymentFlow.Arguments(
                "cb-" + UUID.randomUUID(), sender, Receiver.external(IBAN), Money.of("40.00", "CHF")));
        assertThat(result.kind()).isEqualTo(PaymentResult.Kind.ACCEPTED);
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.SUBMITTED);
        return result.payment();
    }

    private String body(UUID eventId, UUID paymentId, String outcome, String ref) {
        String refJson = ref == null ? "null" : "\"" + ref + "\"";
        return """
                {"eventId":"%s","paymentId":"%s","outcome":"%s","railReference":%s}
                """.formatted(eventId, paymentId, outcome, refJson);
    }

    @Test
    void valid_callback_applies_settlement_and_replay_returns_200() throws Exception {
        Payment payment = createSubmittedExternalPayment();
        UUID eventId = UUID.randomUUID();
        String sig =
                HmacSignature.sign(callbackProperties.hmacSecret(), eventId, payment.getId(), "SETTLED", "SIM-CB-1");

        mvc.perform(post("/internal/rail/callbacks")
                        .header("X-Rail-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(eventId, payment.getId(), "SETTLED", "SIM-CB-1")))
                .andExpect(status().isNoContent());

        Payment completed = payments.findById(payment.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(completed.getRailReference()).isEqualTo("SIM-CB-1");

        // Idempotent replay with same eventId -> 200 OK
        mvc.perform(post("/internal/rail/callbacks")
                        .header("X-Rail-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(eventId, payment.getId(), "SETTLED", "SIM-CB-1")))
                .andExpect(status().isOk());
    }

    @Test
    void invalid_signature_returns_401() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        mvc.perform(post("/internal/rail/callbacks")
                        .header("X-Rail-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(eventId, paymentId, "SETTLED", "SIM-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }

    @Test
    void unknown_payment_returns_404() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        String sig = HmacSignature.sign(callbackProperties.hmacSecret(), eventId, unknownId, "REJECTED", null);

        mvc.perform(post("/internal/rail/callbacks")
                        .header("X-Rail-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(eventId, unknownId, "REJECTED", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void conflicting_outcome_returns_409() throws Exception {
        Payment payment = createSubmittedExternalPayment();
        UUID event1 = UUID.randomUUID();
        String sig1 = HmacSignature.sign(callbackProperties.hmacSecret(), event1, payment.getId(), "REJECTED", null);

        mvc.perform(post("/internal/rail/callbacks")
                        .header("X-Rail-Signature", sig1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(event1, payment.getId(), "REJECTED", null)))
                .andExpect(status().isNoContent());

        UUID event2 = UUID.randomUUID();
        String sig2 =
                HmacSignature.sign(callbackProperties.hmacSecret(), event2, payment.getId(), "SETTLED", "SIM-LATE");
        mvc.perform(post("/internal/rail/callbacks")
                        .header("X-Rail-Signature", sig2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(event2, payment.getId(), "SETTLED", "SIM-LATE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CALLBACK_CONFLICT"));
    }
}
