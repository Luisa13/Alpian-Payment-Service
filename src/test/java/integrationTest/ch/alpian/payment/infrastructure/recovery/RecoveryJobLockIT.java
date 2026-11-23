package ch.alpian.payment.infrastructure.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.alpian.payment.api.generated.model.CreatePaymentRequest;
import ch.alpian.payment.api.generated.model.PaymentResponse;
import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.SettlementRail;
import ch.alpian.payment.integration.PaymentIntegrationTest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Verifies that ShedLock prevents concurrent {@link RecoveryJob#recover()} invocations from issuing
 * duplicate rail calls for the same stale {@code RESERVED} payment.
 */
@TestPropertySource(
        properties = {
            "app.recovery.interval-ms=600000",
            "app.recovery.stale-after=30s",
            "app.recovery.max-attempts=3",
            "app.recovery.lock-at-least=PT30S",
            "app.rail.timeout=150ms"
        })
class RecoveryJobLockIT extends PaymentIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    RecoveryJob recoveryJob;

    @MockitoBean
    SettlementRail rail;

    @BeforeEach
    @AfterEach
    void resetShedLock() {
        jdbc.sql("UPDATE shedlock SET lock_until = locked_at - INTERVAL '1 second' WHERE name = 'recoveryJob'")
                .update();
    }

    @Test
    void concurrent_recover_calls_acquire_lock_once_and_call_rail_once() throws Exception {
        when(rail.settle(any())).thenReturn(SettlementOutcome.unknown());
        when(rail.getStatus(any())).thenReturn(SettlementOutcome.settled("LOCK-REF-001"));

        UUID sender = newAccount("100.00");
        UUID receiver = newAccount("0.00");

        var req = new CreatePaymentRequest()
                .amount("40.00")
                .currency("CHF")
                .senderAccountId(sender)
                .receiverAccountId(receiver);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "recovery-lock-" + UUID.randomUUID());
        ResponseEntity<PaymentResponse> response =
                http.postForEntity("/api/v1/payments", new HttpEntity<>(req, headers), PaymentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID paymentId = response.getBody().getId();

        jdbc.sql("UPDATE payments SET reserved_at = now() - INTERVAL '60 seconds' WHERE id = :id")
                .param("id", paymentId)
                .update();

        // Reset any lock row written by an earlier test before racing the two threads
        resetShedLock();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> {
                ready.countDown();
                await(start);
                recoveryJob.recover();
            });
            Future<?> second = pool.submit(() -> {
                ready.countDown();
                await(start);
                recoveryJob.recover();
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        verify(rail, times(1)).getStatus(argThat(r -> r.paymentId().equals(paymentId)));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for start latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
