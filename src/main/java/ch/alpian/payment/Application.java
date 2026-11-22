package ch.alpian.payment;

import ch.alpian.payment.application.support.RailCallbackProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point. Virtual threads are enabled via {@code spring.threads.virtual.enabled=true} in application.yml. */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RailCallbackProperties.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
