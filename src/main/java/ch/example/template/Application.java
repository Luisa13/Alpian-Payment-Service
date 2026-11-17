package ch.example.template;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. Virtual threads are enabled via {@code spring.threads.virtual.enabled=true} in
 * application.yml — no code changes required.
 *
 * <p>The {@code transfers} package is a sample feature. Replace it entirely when building your
 * actual service; the surrounding infrastructure (observability, error handling, outbox pattern,
 * idempotency, Kafka wiring) is meant to be reused.
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
