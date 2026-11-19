package ch.alpian.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.alpian.payment.api.generated.model.CreatePaymentRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** T6: concurrent reservations never overspend available balance. */
class ReservationConcurrencyIT extends PaymentIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Test
    @Timeout(120)
    void fifty_concurrent_payments_of_10_on_100_balance_leave_10_completed_and_40_rejected() throws Exception {
        UUID sender = newAccount("100.00");
        UUID receiver = newAccount("0.00");

        int total = 50;
        CountDownLatch ready = new CountDownLatch(total);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(total)) {
            for (int i = 0; i < total; i++) {
                String key = "res-" + i + "-" + UUID.randomUUID();
                var body = new CreatePaymentRequest()
                        .amount("10.00")
                        .currency("CHF")
                        .senderAccountId(sender)
                        .receiverAccountId(receiver);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("Idempotency-Key", key);
                    return http.postForEntity("/api/v1/payments", new HttpEntity<>(body, headers), String.class);
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }

            Map<HttpStatusCode, Long> statusCounts = responses.stream()
                    .collect(Collectors.groupingBy(ResponseEntity::getStatusCode, Collectors.counting()));
            assertThat(statusCounts)
                    .containsExactlyInAnyOrderEntriesOf(Map.of(
                            HttpStatus.CREATED, 10L,
                            HttpStatus.UNPROCESSABLE_ENTITY, 40L));
        }

        long completedRows = jdbc.sql(
                        "SELECT COUNT(*) FROM payments WHERE sender_account_id = :sender AND status = 'COMPLETED'")
                .param("sender", sender)
                .query(Long.class)
                .single();
        long rejectedRows = jdbc.sql(
                        "SELECT COUNT(*) FROM payments WHERE sender_account_id = :sender AND status = 'REJECTED'")
                .param("sender", sender)
                .query(Long.class)
                .single();
        assertThat(completedRows).isEqualTo(10);
        assertThat(rejectedRows).isEqualTo(40);

        assertThat(available(sender)).isEqualByComparingTo("0.00");
        assertThat(balance(sender)).isEqualByComparingTo("0.00");
        assertThat(balance(receiver)).isEqualByComparingTo("100.00");
    }
}
