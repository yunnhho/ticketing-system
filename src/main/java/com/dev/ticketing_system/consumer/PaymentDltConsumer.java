package com.dev.ticketing_system.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentDltConsumer {

    @KafkaListener(topics = "payment-completed.DLT", groupId = "ticketing-dlt-group")
    public void consumeDlt(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage) {

        log.error(">>> [DLT] payment message moved to DLT topic={} originalTopic={} originalOffset={} payload={} reason={}",
                topic,
                originalTopic,
                originalOffset,
                message,
                exceptionMessage);
    }
}
