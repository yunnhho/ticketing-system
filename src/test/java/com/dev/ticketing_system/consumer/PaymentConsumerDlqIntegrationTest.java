package com.dev.ticketing_system.consumer;

import com.dev.ticketing_system.config.KafkaConsumerConfig;
import com.dev.ticketing_system.service.PaymentConfirmService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@DirtiesContext
@SpringJUnitConfig(classes = {
        KafkaConsumerConfig.class,
        PaymentConsumer.class,
        PaymentConsumerDlqIntegrationTest.TestKafkaConfig.class
})
@EmbeddedKafka(partitions = 1, topics = {"payment-completed", "payment-completed.DLT"})
class PaymentConsumerDlqIntegrationTest {

    private static final String PAYMENT_TOPIC = "payment-completed";
    private static final String PAYMENT_DLT_TOPIC = "payment-completed.DLT";

    @Configuration
    @EnableKafka
    static class TestKafkaConfig {

        @Bean
        ProducerFactory<String, String> producerFactory(EmbeddedKafkaBroker embeddedKafkaBroker) {
            Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
            return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new StringSerializer());
        }

        @Bean
        KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }

        @Bean
        ConsumerFactory<String, String> consumerFactory(EmbeddedKafkaBroker embeddedKafkaBroker) {
            Map<String, Object> props = KafkaTestUtils.consumerProps("ticketing-group", "false", embeddedKafkaBroker);
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer());
        }

        @Bean
        PaymentConfirmService paymentConfirmService() {
            return Mockito.mock(PaymentConfirmService.class);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @org.springframework.beans.factory.annotation.Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    private PaymentConfirmService paymentConfirmService;

    private Consumer<String, String> dltConsumer;

    @BeforeEach
    void setUp() {
        reset(paymentConfirmService);

        Map<String, Object> props = KafkaTestUtils.consumerProps("payment-dlt-verifier-" + UUID.randomUUID(), "false", embeddedKafkaBroker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        dltConsumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, PAYMENT_DLT_TOPIC);
    }

    @AfterEach
    void tearDown() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
    }

    @Test
    void failedPaymentMessagesArePublishedToDltAfterRetries() throws Exception {
        doThrow(new RuntimeException("consumer failure"))
                .when(paymentConfirmService)
                .confirmPayment(1L, "user1");

        kafkaTemplate.send(PAYMENT_TOPIC, "1:user1").get();

        verify(paymentConfirmService, timeout(10000).times(3)).confirmPayment(1L, "user1");

        ConsumerRecord<String, String> dltRecord = awaitDltRecord("1:user1", Duration.ofSeconds(10));

        assertThat(dltRecord.value()).isEqualTo("1:user1");
        assertThat(new String(dltRecord.headers().lastHeader("kafka_dlt-original-topic").value()))
                .isEqualTo(PAYMENT_TOPIC);
    }

    @Test
    void invalidMessageMovesDirectlyToDltWithoutRetry() throws Exception {
        kafkaTemplate.send(PAYMENT_TOPIC, "invalid-payload").get();

        ConsumerRecord<String, String> dltRecord = awaitDltRecord("invalid-payload", Duration.ofSeconds(10));

        verify(paymentConfirmService, timeout(2000).times(0)).confirmPayment(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        assertThat(dltRecord.value()).isEqualTo("invalid-payload");
    }

    private ConsumerRecord<String, String> awaitDltRecord(String expectedPayload, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<String> seenPayloads = new ArrayList<>();

        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofMillis(250));

            for (ConsumerRecord<String, String> record : records) {
                seenPayloads.add(record.value());
                if (expectedPayload.equals(record.value())) {
                    return record;
                }
            }
        }

        throw new AssertionError("Expected DLT payload not found. expected=" + expectedPayload + ", seen=" + seenPayloads);
    }
}
