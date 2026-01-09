package com.dev.ticketing_system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final String TOPIC = "payment-completed";

    /**
     * 결제 완료 이벤트를 Kafka로 발행합니다.
     * 실제 환경에서는 결제 게이트웨이(PG사) 승인 후 호출됩니다.
     */
    public void sendPaymentEvent(Long seatId, String userId) {
        // "seatId:userId" 형태로 메시지 구성
        String message = seatId + ":" + userId;
        kafkaTemplate.send(TOPIC, message);
    }
}