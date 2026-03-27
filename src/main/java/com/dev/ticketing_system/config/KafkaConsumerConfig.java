package com.dev.ticketing_system.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    /**
     * 커스텀 리스너 컨테이너 팩토리 빈 등록
     * 이 빈이 있으면 @KafkaListener는 자동으로 이 설정을 사용합니다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // -------------------------------------------------------
        // [DLQ 핵심 설정]
        // -------------------------------------------------------

        // 1. Recoverer: 실패 시 메시지를 DLT 토픽(기존토픽명.DLT)으로 전송
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );

        // 2. BackOff: 1초 간격으로 2번만 재시도(Retry)
        // (즉, 총 3번 시도 후에도 실패하면 포기하고 DLT로 보냄)
        FixedBackOff backOff = new FixedBackOff(1000L, 2);

        // 3. ErrorHandler: 위 두 설정을 결합하여 등록
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Kafka retry attempt={} topic={} payload={} reason={}",
                        deliveryAttempt,
                        record.topic(),
                        record.value(),
                        ex.getMessage()));
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
