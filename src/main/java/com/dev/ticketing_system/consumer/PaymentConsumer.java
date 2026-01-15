package com.dev.ticketing_system.consumer;

import com.dev.ticketing_system.service.PaymentConfirmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConsumer {

    private final PaymentConfirmService paymentConfirmService;

    @KafkaListener(topics = "payment-completed", groupId = "ticketing-group")
    public void consume(String message) {
        log.info(">>> [Consumer] payment-completed 수신: {}", message);

        try {
            String[] data = message.split(":");
            Long seatId = Long.parseLong(data[0]);
            String userId = data[1];

            paymentConfirmService.confirmPayment(seatId, userId);

        } catch (Exception e) {
            // Kafka 재처리를 막기 위해 예외를 삼킴 (멱등 구조)
            log.error(">>> [Consumer] 결제 처리 중 오류 (무시 처리): {}", e.getMessage(), e);
        }
    }
}
