package ch.example.template.transfer.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Base class for integration tests.
 *
 * <p>PostgreSQL and Kafka containers are started once per JVM via a static initializer and kept
 * alive for all subclass test suites. {@link ServiceConnection} wires container coordinates into
 * Spring Boot's auto-configuration automatically.
 *
 * <p>No {@code @Testcontainers}/{@code @Container} annotations are used here: managing lifecycle
 * manually avoids the per-subclass restart that JUnit 5's Testcontainers extension would otherwise
 * apply to static fields in an abstract superclass.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");

    static {
        postgres.start();
        kafka.start();
    }
}
