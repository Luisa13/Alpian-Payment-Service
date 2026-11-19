package ch.alpian.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.alpian.payment.api.generated.model.AccountResponse;
import ch.alpian.payment.api.generated.model.CreatePaymentRequest;
import ch.alpian.payment.api.generated.model.PaymentResponse;
import java.math.BigDecimal;
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

/** T9: full create-payment orchestration and bidirectional concurrency without deadlocks. */
class CreatePaymentOrchestrationIT extends PaymentIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Test
    void completed_payment_returns_201_and_is_queryable_via_get_endpoints() {
        UUID sender = newAccount("500.00");
        UUID receiver = newAccount("100.00");

        ResponseEntity<PaymentResponse> created = postPayment(sender, receiver, "75.50");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse body = created.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(PaymentResponse.StatusEnum.COMPLETED);

        ResponseEntity<PaymentResponse> fetched =
                http.getForEntity("/api/v1/payments/{id}", PaymentResponse.class, body.getId());
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().getStatus()).isEqualTo(PaymentResponse.StatusEnum.COMPLETED);

        ResponseEntity<AccountResponse> senderAcc =
                http.getForEntity("/api/v1/accounts/{id}", AccountResponse.class, sender);
        assertThat(senderAcc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new BigDecimal(senderAcc.getBody().getBalance())).isEqualByComparingTo("424.50");
        assertThat(new BigDecimal(senderAcc.getBody().getAvailableBalance())).isEqualByComparingTo("424.50");
    }

    @Test
    @Timeout(120)
    void hundred_concurrent_bidirectional_transfers_finish_with_total_money_unchanged() throws Exception {
        UUID accountA = newAccount("1000.00");
        UUID accountB = newAccount("1000.00");

        int total = 100;
        CountDownLatch ready = new CountDownLatch(total);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(total)) {
            for (int i = 0; i < total; i++) {
                boolean aToB = (i % 2 == 0);
                UUID from = aToB ? accountA : accountB;
                UUID to = aToB ? accountB : accountA;
                String amount = aToB ? "5.00" : "3.00";
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
                    return postPaymentRaw(from, to, amount);
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
                    .describedAs(
                            "responses: %s",
                            responses.stream()
                                    .filter(r -> r.getStatusCode() != HttpStatus.CREATED)
                                    .map(ResponseEntity::getBody)
                                    .toList())
                    .containsExactlyInAnyOrderEntriesOf(Map.of(HttpStatus.CREATED, 100L));
        }

        // 50 * 5.00 = 250.00 from A -> B; 50 * 3.00 = 150.00 from B -> A
        assertThat(balance(accountA)).isEqualByComparingTo("900.00");
        assertThat(available(accountA)).isEqualByComparingTo("900.00");
        assertThat(balance(accountB)).isEqualByComparingTo("1100.00");
        assertThat(available(accountB)).isEqualByComparingTo("1100.00");
        assertThat(balance(accountA).add(balance(accountB))).isEqualByComparingTo("2000.00");
    }

    private ResponseEntity<PaymentResponse> postPayment(UUID sender, UUID receiver, String amount) {
        return postPaymentEntity(sender, receiver, amount, PaymentResponse.class);
    }

    private ResponseEntity<String> postPaymentRaw(UUID sender, UUID receiver, String amount) {
        return postPaymentEntity(sender, receiver, amount, String.class);
    }

    private <T> ResponseEntity<T> postPaymentEntity(UUID sender, UUID receiver, String amount, Class<T> responseType) {
        var req = new CreatePaymentRequest()
                .amount(amount)
                .currency("CHF")
                .senderAccountId(sender)
                .receiverAccountId(receiver);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "orch-" + UUID.randomUUID());
        return http.postForEntity("/api/v1/payments", new HttpEntity<>(req, headers), responseType);
    }
}
