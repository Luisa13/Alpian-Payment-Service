package ch.alpian.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.alpian.payment.api.generated.model.CreatePaymentRequest;
import ch.alpian.payment.api.generated.model.PaymentResponse;
import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.SettlementRail;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** T9: orchestration behaviour when the rail rejects or times out. */
@TestPropertySource(properties = "app.rail.timeout=150ms")
class RailOutcomeOrchestrationIT extends PaymentIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @MockitoBean
    SettlementRail rail;

    @Test
    void rail_rejected_returns_201_with_failed_status_and_releases_hold() {
        when(rail.settle(any())).thenReturn(SettlementOutcome.rejected());

        UUID sender = newAccount("200.00");
        UUID receiver = newAccount("0.00");

        ResponseEntity<PaymentResponse> response = postPayment(sender, receiver, "40.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(PaymentResponse.StatusEnum.FAILED);
        assertThat(balance(sender)).isEqualByComparingTo("200.00");
        assertThat(available(sender)).isEqualByComparingTo("200.00");
        assertThat(balance(receiver)).isEqualByComparingTo("0.00");
    }

    @Test
    void rail_timeout_returns_202_with_location_and_leaves_payment_reserved() {
        when(rail.settle(any())).thenAnswer(inv -> {
            Thread.sleep(500);
            return SettlementOutcome.settled("LATE");
        });

        UUID sender = newAccount("200.00");
        UUID receiver = newAccount("0.00");

        ResponseEntity<PaymentResponse> response = postPayment(sender, receiver, "40.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        PaymentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(PaymentResponse.StatusEnum.RESERVED);
        assertThat(response.getHeaders().getLocation()).hasPath("/api/v1/payments/" + body.getId());

        assertThat(balance(sender)).isEqualByComparingTo("200.00");
        assertThat(available(sender)).isEqualByComparingTo("160.00");
        Integer attempts = jdbc.sql("SELECT settlement_attempts FROM payments WHERE id = :id")
                .param("id", body.getId())
                .query(Integer.class)
                .single();
        assertThat(attempts).isEqualTo(1);
    }

    private ResponseEntity<PaymentResponse> postPayment(UUID sender, UUID receiver, String amount) {
        var req = new CreatePaymentRequest()
                .amount(amount)
                .currency("CHF")
                .senderAccountId(sender)
                .receiverAccountId(receiver);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "rail-" + UUID.randomUUID());
        return http.postForEntity("/api/v1/payments", new HttpEntity<>(req, headers), PaymentResponse.class);
    }
}
