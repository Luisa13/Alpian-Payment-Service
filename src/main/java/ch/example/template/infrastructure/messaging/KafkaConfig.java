package ch.example.template.infrastructure.messaging;

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
 * <p>Retry policy: exponential backoff 500 ms initial, factor 2, up to 3 retries. Non-retryable
 * exceptions (schema/deserialisation errors) skip retries and go directly to DLT.
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

        handler.addNotRetryableExceptions(IllegalArgumentException.class, StreamReadException.class);

        return handler;
    }
}
