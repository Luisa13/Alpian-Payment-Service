package ch.alpian.payment.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.alpian.payment.api.generated.model.AccountResponse;
import ch.alpian.payment.api.generated.model.CreatePaymentRequest;
import ch.alpian.payment.api.generated.model.PaymentResponse;
import ch.alpian.payment.integration.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * End-to-end test covering the full vertical slice: HTTP intake → PostgreSQL reserve/settle/ledger
 * → OutboxRelay → Kafka → NotificationConsumer → sent_notifications.
 */
class PaymentLifecycleE2ETest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcClient jdbc;

    @Test
    void internal_payment_completes_updates_balances_replays_idempotently_and_delivers_notifications() {
        UUID sender = createAccount("1000.00");
        UUID receiver = createAccount("250.00");
        String idempotencyKey = "e2e-" + UUID.randomUUID();

        var request = new CreatePaymentRequest()
                .senderAccountId(sender)
                .receiverAccountId(receiver)
                .amount("125.50")
                .currency("CHF");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        // 1. Create payment via HTTP
        ResponseEntity<PaymentResponse> created =
                http.postForEntity("/api/v1/payments", new HttpEntity<>(request, headers), PaymentResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        UUID paymentId = created.getBody().getId();
        assertThat(created.getBody().getStatus()).isEqualTo(PaymentResponse.StatusEnum.COMPLETED);

        // 2. Read payment back via HTTP
        ResponseEntity<PaymentResponse> fetched =
                http.getForEntity("/api/v1/payments/{id}", PaymentResponse.class, paymentId);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isNotNull();
        assertThat(fetched.getBody().getId()).isEqualTo(paymentId);
        assertThat(fetched.getBody().getStatus()).isEqualTo(PaymentResponse.StatusEnum.COMPLETED);

        // 3. Verify updated account balances via HTTP
        ResponseEntity<AccountResponse> senderAccount =
                http.getForEntity("/api/v1/accounts/{id}", AccountResponse.class, sender);
        ResponseEntity<AccountResponse> receiverAccount =
                http.getForEntity("/api/v1/accounts/{id}", AccountResponse.class, receiver);
        assertThat(senderAccount.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(receiverAccount.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new BigDecimal(senderAccount.getBody().getBalance())).isEqualByComparingTo("874.50");
        assertThat(new BigDecimal(senderAccount.getBody().getAvailableBalance()))
                .isEqualByComparingTo("874.50");
        assertThat(new BigDecimal(receiverAccount.getBody().getBalance())).isEqualByComparingTo("375.50");

        // 4. Idempotent replay returns 200 OK with the same payment ID and no double debit
        ResponseEntity<PaymentResponse> replayed =
                http.postForEntity("/api/v1/payments", new HttpEntity<>(request, headers), PaymentResponse.class);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayed.getBody()).isNotNull();
        assertThat(replayed.getBody().getId()).isEqualTo(paymentId);

        ResponseEntity<AccountResponse> senderAfterReplay =
                http.getForEntity("/api/v1/accounts/{id}", AccountResponse.class, sender);
        assertThat(new BigDecimal(senderAfterReplay.getBody().getBalance())).isEqualByComparingTo("874.50");

        // 5. Outbox relay publishes to Kafka and NotificationConsumer records both lifecycle notifications
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<String> eventTypes = jdbc.sql(
                            "SELECT event_type FROM sent_notifications WHERE payment_id = :id ORDER BY sent_at")
                    .param("id", paymentId)
                    .query(String.class)
                    .list();
            assertThat(eventTypes).containsExactlyInAnyOrder("PAYMENT_RESERVED", "PAYMENT_COMPLETED");
        });
    }

    private UUID createAccount(String balance) {
        UUID id = UUID.randomUUID();
        BigDecimal amount = new BigDecimal(balance);
        jdbc.sql("""
                        INSERT INTO accounts (id, owner_name, account_type, currency, balance, available_balance)
                        VALUES (:id, 'E2E account', 'CUSTOMER', 'CHF', :amount, :amount)
                        """).param("id", id).param("amount", amount).update();
        jdbc.sql(
                        "INSERT INTO ledger_entries (id, payment_id, account_id, amount) VALUES (:id, NULL, :account, :amount)")
                .param("id", UUID.randomUUID())
                .param("account", id)
                .param("amount", amount)
                .update();
        return id;
    }
}
