package ch.alpian.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.alpian.payment.api.generated.model.CreatePaymentRequest;
import ch.alpian.payment.api.generated.model.PaymentResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** T5: idempotency under concurrency and key reuse with a different payload. */
class IdempotencyIT extends PaymentIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Test
    void twenty_concurrent_requests_with_the_same_key_create_one_payment() throws Exception {
        UUID sender = newAccount("1000.00");
        UUID receiver = newAccount("0.00");
        String key = "idem-" + UUID.randomUUID();
        var body = new CreatePaymentRequest()
                .amount("100.00")
                .currency("CHF")
                .senderAccountId(sender)
                .receiverAccountId(receiver);

        int threads = 20;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<PaymentResponse>>> futures = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return postPayment(key, body, PaymentResponse.class);
                }));
            }
            ready.await();
            start.countDown();

            List<ResponseEntity<PaymentResponse>> responses = new ArrayList<>();
            for (Future<ResponseEntity<PaymentResponse>> future : futures) {
                responses.add(future.get());
            }

            assertThat(responses)
                    .allSatisfy(r -> assertThat(r.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK));
            List<UUID> ids =
                    responses.stream().map(r -> r.getBody().getId()).distinct().toList();
            assertThat(ids).hasSize(1);

            UUID paymentId = ids.getFirst();
            long rows = jdbc.sql("SELECT COUNT(*) FROM payments WHERE idempotency_key = :key")
                    .param("key", key)
                    .query(Long.class)
                    .single();
            assertThat(rows).isEqualTo(1);
            assertThat(balance(sender)).isEqualByComparingTo("900.00");
            assertThat(available(sender)).isEqualByComparingTo("900.00");
            assertThat(balance(receiver)).isEqualByComparingTo("100.00");
            assertThat(ledgerRows(paymentId)).isEqualTo(2);
        }
    }

    @Test
    void same_key_with_a_different_body_returns_422() {
        UUID sender = newAccount("500.00");
        UUID receiver = newAccount("0.00");
        String key = "idem-conflict-" + UUID.randomUUID();

        var first = new CreatePaymentRequest()
                .amount("10.00")
                .currency("CHF")
                .senderAccountId(sender)
                .receiverAccountId(receiver);
        var second = new CreatePaymentRequest()
                .amount("20.00")
                .currency("CHF")
                .senderAccountId(sender)
                .receiverAccountId(receiver);

        ResponseEntity<PaymentResponse> created = postPayment(key, first, PaymentResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> conflict = postPayment(key, second, String.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(conflict.getBody()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(balance(sender)).isEqualByComparingTo("490.00");
    }

    private <T> ResponseEntity<T> postPayment(String key, CreatePaymentRequest body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        return http.postForEntity("/api/v1/payments", new HttpEntity<>(body, headers), responseType);
    }
}
