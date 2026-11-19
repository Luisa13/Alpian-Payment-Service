package ch.alpian.payment.infrastructure.messaging;

import ch.alpian.payment.application.events.PaymentEvents;
import com.fasterxml.jackson.core.exc.StreamReadException;
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
import org.apache.kafka.clients.admin.NewTopic;

/**
 * Kafka topics and listener container configuration.
 *
 * <p>Retry policy: exponential backoff 500 ms initial, factor 2, up to 3 retries, then the record
 * goes to {@code payment-notifications.DLT}. Non-retryable exceptions (schema/deserialisation
 * errors) skip retries and go straight to the DLT.
 */
@Configuration
public class KafkaConfig {

    @Bean
    NewTopic paymentNotificationsTopic() {
        return TopicBuilder.name(PaymentEvents.TOPIC).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic paymentNotificationsDlt() {
        return TopicBuilder.name(PaymentEvents.DLT_TOPIC).partitions(6).replicas(1).build();
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
        var backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxAttempts(3);

        // Default destination resolver: <original topic>.DLT, same partition number.
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        var handler = new DefaultErrorHandler(recoverer, backOff);

        handler.addNotRetryableExceptions(IllegalArgumentException.class, StreamReadException.class);

        return handler;
    }
}
