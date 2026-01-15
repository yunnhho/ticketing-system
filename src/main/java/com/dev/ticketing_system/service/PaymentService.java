package com.dev.ticketing_system.service;

import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.exception.SeatAlreadyTakenException;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RedissonClient redissonClient;
    private final SeatRepository seatRepository;

    private static final String TOPIC = "payment-completed";
    private static final String LOCK_KEY = "seat:lock:";

    @Transactional(readOnly = true)
    public void validateAndPay(Long seatId, String userId) {
        String lockKey = LOCK_KEY + seatId;
        String userKey = lockKey + ":user";

        RLock lock = redissonClient.getLock(lockKey);
        RBucket<String> userBucket = redissonClient.getBucket(userKey);

        if (!lock.isLocked()) {
            throw new SeatAlreadyTakenException("결제 시간이 초과되었습니다.");
        }

        String ownerId = userBucket.get();
        if (ownerId == null || !ownerId.equals(userId)) {
            throw new SeatAlreadyTakenException("좌석 점유 정보가 일치하지 않습니다.");
        }

        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 좌석입니다."));

        if (seat.getStatus() == Seat.SeatStatus.SOLD) {
            throw new SeatAlreadyTakenException("이미 결제 완료된 좌석입니다.");
        }

        // ⭐ Write-Behind
        kafkaTemplate.send(TOPIC, seatId + ":" + userId);
        log.info(">>> Kafka 결제 이벤트 발행 완료 (seatId={}, userId={})", seatId, userId);
    }
}
