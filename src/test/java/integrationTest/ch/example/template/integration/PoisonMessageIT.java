package ch.example.template.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.utils.ContainerTestUtils;

class PoisonMessageIT extends IntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    @Test
    void poison_message_lands_in_dlt_and_does_not_block_valid_messages() throws Exception {
        var dltRecords = Collections.synchronizedList(new java.util.ArrayList<ConsumerRecord<String, String>>());
        var dltProps = new ContainerProperties("payments.requested-dlt");
        dltProps.setGroupId("test-dlt-" + UUID.randomUUID());
        dltProps.setMessageListener((MessageListener<String, String>) dltRecords::add);
        var dltContainer = new KafkaMessageListenerContainer<>(consumerFactory, dltProps);
        dltContainer.start();
        ContainerTestUtils.waitForAssignment(dltContainer, 1);

        try {
            kafkaTemplate.send(
                    new ProducerRecord<>("payments.requested", UUID.randomUUID().toString(), "NOT_VALID_JSON{{{"));
            kafkaTemplate.flush();

            await().atMost(Duration.ofSeconds(30)).until(() -> !dltRecords.isEmpty());

            assertThat(dltRecords).isNotEmpty();
            assertThat(dltRecords.get(0).topic()).isEqualTo("payments.requested-dlt");
        } finally {
            dltContainer.stop();
        }
    }
}
