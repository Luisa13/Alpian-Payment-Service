package ch.alpian.payment.infrastructure.messaging;

import ch.alpian.payment.application.events.PaymentEvents;
import com.fasterxml.jackson.core.exc.StreamReadException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka topics and listener container configuration.
 *
 * <p>Retry policy: exponential backoff with configurable initial interval, multiplier, and max
 * attempts (see {@code app.kafka.consumer-retry.*}), then the record goes to {@code
 * payment-notifications.DLT}. Non-retryable exceptions (schema/deserialisation errors) skip
 * retries and go straight to the DLT.
 */
@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConfig {

    private final KafkaProperties properties;

    public KafkaConfig(KafkaProperties properties) {
        this.properties = properties;
    }

    @Bean
    NewTopic paymentNotificationsTopic() {
        return TopicBuilder.name(PaymentEvents.TOPIC)
                .partitions(properties.topics().partitions())
                .replicas(properties.topics().replicas())
                .build();
    }

    @Bean
    NewTopic paymentNotificationsDlt() {
        return TopicBuilder.name(PaymentEvents.DLT_TOPIC)
                .partitions(properties.topics().partitions())
                .replicas(properties.topics().replicas())
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));
        return factory;
    }

    private CommonErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        var retry = properties.consumerRetry();
        var backOff = new ExponentialBackOff(retry.initialIntervalMs(), retry.multiplier());
        backOff.setMaxAttempts(retry.maxAttempts());

        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (cr, ex) -> new TopicPartition(PaymentEvents.DLT_TOPIC, cr.partition()));
        var handler = new DefaultErrorHandler(recoverer, backOff);

        handler.addNotRetryableExceptions(IllegalArgumentException.class, StreamReadException.class);

        return handler;
    }
}
