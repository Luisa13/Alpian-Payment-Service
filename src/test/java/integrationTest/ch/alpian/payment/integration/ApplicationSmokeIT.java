package ch.alpian.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

/** T1: the application starts against real Postgres and Kafka and reports itself healthy. */
class ApplicationSmokeIT extends IntegrationTestBase {

    @Autowired
    TestRestTemplate http;

    @Test
    void application_starts_and_is_healthy() {
        var response = http.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
