package ch.example.template.config;

import com.fasterxml.jackson.core.exc.StreamReadException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka listener container configuration.
 *
 * <p>Retry policy:
 *
 * <ul>
 *   <li>Exponential backoff: 500 ms initial, factor 2, up to 3 retries (max ~3.5 s delay).
 *   <li>Non-retryable exceptions (schema/deserialisation errors) skip retries and go directly to
 *       DLT.
 *   <li>All other exceptions are retried; after exhaustion the message is published to the {@code
 *       .DLT} topic.
 * </ul>
 */
@Configuration
public class KafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));
        return factory;
    }

    private CommonErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        var backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxAttempts(3);

        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        var handler = new DefaultErrorHandler(recoverer, backOff);

        // These exceptions indicate a schema / deserialisation problem.
        // Retrying is pointless — route directly to DLT.
        handler.addNotRetryableExceptions(IllegalArgumentException.class, StreamReadException.class);

        return handler;
    }
}
