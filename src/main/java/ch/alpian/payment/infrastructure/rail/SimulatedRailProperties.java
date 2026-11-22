package ch.alpian.payment.infrastructure.rail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Behaviour of the simulated external rail.
 *
 * @param delay         how long every call takes
 * @param deliveryDelay how long after acceptance the dispatcher fires the callback
 * @param rejectionRate share of new transfers the rail rejects (0..1)
 * @param timeoutRate   share of calls that end UNKNOWN (0..1); nothing is remembered for those
 * @param duplicateRate share of delivered callbacks sent twice for chaos testing (0..1)
 * @param dropRate      share of callbacks dropped instead of POSTed for chaos testing (0..1)
 * @param callbackUrl   target URL for the simulated callback POST (overridden by local port in tests)
 */
@ConfigurationProperties("app.rail.simulated")
public record SimulatedRailProperties(
        Duration delay,
        Duration deliveryDelay,
        double rejectionRate,
        double timeoutRate,
        double duplicateRate,
        double dropRate,
        String callbackUrl) {

    @ConstructorBinding
    public SimulatedRailProperties {
        if (delay == null) delay = Duration.ZERO;
        if (deliveryDelay == null) deliveryDelay = Duration.ZERO;
        requireRate(rejectionRate, "rejection-rate");
        requireRate(timeoutRate, "timeout-rate");
        requireRate(duplicateRate, "duplicate-rate");
        requireRate(dropRate, "drop-rate");
    }

    public SimulatedRailProperties(Duration delay, Duration deliveryDelay, double rejectionRate, double timeoutRate) {
        this(delay, deliveryDelay, rejectionRate, timeoutRate, 0.0, 0.0, null);
    }

    private static void requireRate(double rate, String name) {
        if (rate < 0 || rate > 1) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
