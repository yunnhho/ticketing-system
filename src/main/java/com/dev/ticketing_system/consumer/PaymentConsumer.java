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

        String[] data = message.split(":");
        if (data.length != 2) {
            throw new IllegalArgumentException("Invalid payment message format: " + message);
        }

        Long seatId = Long.parseLong(data[0]);
        String userId = data[1];

        paymentConfirmService.confirmPayment(seatId, userId);
    }
}
