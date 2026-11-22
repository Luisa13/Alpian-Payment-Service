package ch.alpian.payment.infrastructure.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.alpian.payment.api.generated.model.CreatePaymentRequest;
import ch.alpian.payment.api.generated.model.PaymentResponse;
import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.SettlementRail;
import ch.alpian.payment.integration.PaymentIntegrationTest;
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

/**
 * Recovery job: stale RESERVED payments are retried, exhausted ones are left alone.
 *
 * <p>The scheduler is disabled (interval set to 10 minutes) so tests call {@code recover()}
 * directly and avoid timing races.
 *
 * <p>Recovery checks the rail status before re-submitting:
 *
 * <ul>
 *   <li>If the rail has a stored outcome (SETTLED/REJECTED), it is used directly — no re-submit.
 *   <li>If the rail has no record (UNKNOWN), the payment is re-submitted via {@code settle()}.
 * </ul>
 */
@TestPropertySource(
        properties = {
            "app.recovery.interval-ms=600000",
            "app.recovery.stale-after=30s",
            "app.recovery.max-attempts=3",
            "app.rail.timeout=150ms"
        })
class RecoveryJobIT extends PaymentIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    RecoveryJob recoveryJob;

    @MockitoBean
    SettlementRail rail;

    @Test
    void stale_reserved_payment_is_completed_by_recovery_via_resubmit() {
        // inline settle() → UNKNOWN; getStatus() → UNKNOWN (rail never got it); re-submit → SETTLED
        when(rail.settle(any()))
                .thenReturn(SettlementOutcome.unknown())
                .thenReturn(SettlementOutcome.settled("RECOVERY-REF-001"));
        when(rail.getStatus(any())).thenReturn(SettlementOutcome.unknown());

        UUID sender = newAccount("100.00");
        UUID receiver = newAccount("0.00");

        ResponseEntity<PaymentResponse> response = postPayment(sender, receiver, "40.00");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID paymentId = response.getBody().getId();

        backdateReservedAt(paymentId, 60);
        recoveryJob.recover();

        assertThat(paymentStatus(paymentId)).isEqualTo("COMPLETED");
        assertThat(available(sender)).isEqualByComparingTo("60.00");
        assertThat(balance(sender)).isEqualByComparingTo("60.00");
        assertThat(balance(receiver)).isEqualByComparingTo("40.00");
        assertThat(outboxEvents(paymentId, "PAYMENT_COMPLETED")).isEqualTo(1);
        // settle() called twice: once inline, once by recovery re-submit
        verify(rail, times(2)).settle(any());
    }

    @Test
    void recovery_uses_stored_rail_status_without_resubmitting() {
        // inline settle() → UNKNOWN (timed out); but rail actually processed it
        // getStatus() → SETTLED; recovery must NOT call settle() again
        when(rail.settle(any())).thenReturn(SettlementOutcome.unknown());
        when(rail.getStatus(any())).thenReturn(SettlementOutcome.settled("STORED-REF-001"));

        UUID sender = newAccount("100.00");
        UUID receiver = newAccount("0.00");

        ResponseEntity<PaymentResponse> response = postPayment(sender, receiver, "40.00");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID paymentId = response.getBody().getId();

        backdateReservedAt(paymentId, 60);
        recoveryJob.recover();

        assertThat(paymentStatus(paymentId)).isEqualTo("COMPLETED");
        assertThat(balance(receiver)).isEqualByComparingTo("40.00");
        assertThat(outboxEvents(paymentId, "PAYMENT_COMPLETED")).isEqualTo(1);
        // settle() called only once (the initial inline call); recovery used getStatus() result
        verify(rail, times(1)).settle(any());
        verify(rail, times(1)).getStatus(any());
    }

    @Test
    void stale_reserved_payment_is_failed_when_rail_rejects_on_resubmit() {
        // inline settle() → UNKNOWN; getStatus() → UNKNOWN; re-submit → REJECTED
        when(rail.settle(any())).thenReturn(SettlementOutcome.unknown()).thenReturn(SettlementOutcome.rejected());
        when(rail.getStatus(any())).thenReturn(SettlementOutcome.unknown());

        UUID sender = newAccount("100.00");
        UUID receiver = newAccount("0.00");

        ResponseEntity<PaymentResponse> response = postPayment(sender, receiver, "40.00");
        UUID paymentId = response.getBody().getId();

        backdateReservedAt(paymentId, 60);
        recoveryJob.recover();

        assertThat(paymentStatus(paymentId)).isEqualTo("FAILED");
        assertThat(available(sender)).isEqualByComparingTo("100.00");
        assertThat(balance(sender)).isEqualByComparingTo("100.00");
        assertThat(outboxEvents(paymentId, "PAYMENT_FAILED")).isEqualTo(1);
    }

    @Test
    void payment_with_exhausted_attempts_is_not_retried() {
        when(rail.settle(any())).thenReturn(SettlementOutcome.unknown());

        UUID sender = newAccount("100.00");
        UUID receiver = newAccount("0.00");

        ResponseEntity<PaymentResponse> response = postPayment(sender, receiver, "40.00");
        UUID paymentId = response.getBody().getId();

        // set attempts to max (3 = app.recovery.max-attempts in this test class)
        jdbc.sql(
                        "UPDATE payments SET reserved_at = now() - INTERVAL '60 seconds', settlement_attempts = 3 WHERE id = :id")
                .param("id", paymentId)
                .update();

        recoveryJob.recover();

        verify(rail, times(1)).settle(any()); // only the initial inline call
        verify(rail, never()).getStatus(any());
        assertThat(paymentStatus(paymentId)).isEqualTo("RESERVED");
    }

    @Test
    void fresh_reserved_payment_is_not_picked_up_before_stale_threshold() {
        when(rail.settle(any())).thenReturn(SettlementOutcome.unknown());

        UUID sender = newAccount("100.00");
        UUID receiver = newAccount("0.00");

        postPayment(sender, receiver, "40.00");
        // payment is fresh (reserved_at = now); do NOT backdate

        recoveryJob.recover();

        verify(rail, times(1)).settle(any()); // only the initial inline call
        verify(rail, never()).getStatus(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<PaymentResponse> postPayment(UUID sender, UUID receiver, String amount) {
        var req = new CreatePaymentRequest()
                .amount(amount)
                .currency("CHF")
                .senderAccountId(sender)
                .receiverAccountId(receiver);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "recovery-" + UUID.randomUUID());
        return http.postForEntity("/api/v1/payments", new HttpEntity<>(req, headers), PaymentResponse.class);
    }

    private void backdateReservedAt(UUID paymentId, int seconds) {
        jdbc.sql("UPDATE payments SET reserved_at = now() - INTERVAL '" + seconds + " seconds' WHERE id = :id")
                .param("id", paymentId)
                .update();
    }

    private String paymentStatus(UUID paymentId) {
        return jdbc.sql("SELECT status FROM payments WHERE id = :id")
                .param("id", paymentId)
                .query(String.class)
                .single();
    }
}
