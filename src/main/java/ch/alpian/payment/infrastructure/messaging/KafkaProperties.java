package ch.alpian.payment.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka topology and consumer-retry settings bound from {@code app.kafka.*}.
 *
 * @param topics       partition / replica counts applied when topics are auto-created
 * @param consumerRetry exponential-backoff parameters for the DLT error handler
 */
@ConfigurationProperties("app.kafka")
public record KafkaProperties(Topics topics, ConsumerRetry consumerRetry) {

    public record Topics(int partitions, int replicas) {}

    public record ConsumerRetry(long initialIntervalMs, double multiplier, int maxAttempts) {}
}
