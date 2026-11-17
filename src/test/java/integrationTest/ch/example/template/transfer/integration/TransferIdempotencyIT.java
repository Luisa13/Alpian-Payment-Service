package ch.example.template.transfer.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Verifies HTTP-level idempotency for POST /v1/transfers.
 *
 * <ul>
 *   <li>Same key + same body → same 201 response
 *   <li>Same key + different body → 409 Conflict
 * </ul>
 */
class TransferIdempotencyIT extends IntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    private static final String BODY_A = """
            {
              "amount": "50.00",
              "currency": "CHF",
              "sourceAccountId": "b0000000-0000-0000-0000-000000000001",
              "targetAccountId": "b0000000-0000-0000-0000-000000000002",
              "description": "idempotency test"
            }
            """;

    private static final String BODY_B = """
            {
              "amount": "99.00",
              "currency": "EUR",
              "sourceAccountId": "b0000000-0000-0000-0000-000000000001",
              "targetAccountId": "b0000000-0000-0000-0000-000000000002",
              "description": "different body"
            }
            """;

    @Test
    void same_key_same_body_returns_201_both_times() {
        String key = UUID.randomUUID().toString();

        ResponseEntity<String> first = post(key, BODY_A);
        ResponseEntity<String> second = post(key, BODY_A);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).isEqualTo(second.getBody());
    }

    @Test
    void same_key_different_body_returns_409() {
        String key = UUID.randomUUID().toString();

        ResponseEntity<String> first = post(key, BODY_A);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> conflict = post(key, BODY_B);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void missing_idempotency_key_returns_400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp =
                restTemplate.postForEntity("/v1/transfers", new HttpEntity<>(BODY_A, headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> post(String idempotencyKey, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.postForEntity("/v1/transfers", new HttpEntity<>(body, headers), String.class);
    }
}
