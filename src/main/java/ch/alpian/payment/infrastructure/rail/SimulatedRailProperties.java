package ch.alpian.payment.infrastructure.rail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Behaviour of the simulated external rail.
 *
 * @param delay         how long every call takes
 * @param deliveryDelay how long after acceptance the dispatcher fires the callback
 * @param rejectionRate share of new transfers the rail rejects (0..1)
 * @param timeoutRate   share of calls that end UNKNOWN (0..1); nothing is remembered for those
 */
@ConfigurationProperties("app.rail.simulated")
public record SimulatedRailProperties(
        Duration delay, Duration deliveryDelay, double rejectionRate, double timeoutRate) {

    public SimulatedRailProperties {
        if (delay == null) delay = Duration.ZERO;
        if (deliveryDelay == null) deliveryDelay = Duration.ZERO;
        requireRate(rejectionRate, "rejection-rate");
        requireRate(timeoutRate, "timeout-rate");
    }

    private static void requireRate(double rate, String name) {
        if (rate < 0 || rate > 1) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
