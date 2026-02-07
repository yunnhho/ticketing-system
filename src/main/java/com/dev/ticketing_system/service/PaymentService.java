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

import java.util.concurrent.TimeUnit;

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
    public void validateAndPay(Long seatId, String userId, String idempotencyKey) {
        String idemKey = "idempotency:" + idempotencyKey;
        RBucket<String> idempotencyBucket = redissonClient.getBucket(idemKey);

        if (idempotencyBucket.isExists() && !"PROCESSING".equals(idempotencyBucket.get())) {
            throw new IllegalArgumentException("이미 완료된 요청입니다.");
        }
        idempotencyBucket.set("PROCESSING", 10, TimeUnit.MINUTES);

        try {
            String lockKey = LOCK_KEY + seatId;
            String userKey = lockKey + ":user";

            RLock lock = redissonClient.getLock(lockKey);
            RBucket<String> userBucket = redissonClient.getBucket(userKey);

            if (!lock.isLocked()) {
                throw new SeatAlreadyTakenException("결제 시간이 초과되어 좌석 선점이 해제되었습니다.");
            }

            String ownerId = userBucket.get();

            if (ownerId == null || !ownerId.equals(userId)) {
                log.warn("락 소유자 불일치: 요청={}, 실제={}", userId, ownerId);
                throw new SeatAlreadyTakenException("좌석 점유 권한이 없습니다. (다른 유저가 선점 중)");
            }

            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 좌석입니다."));

            if (seat.getStatus() == Seat.SeatStatus.SOLD) {
                throw new SeatAlreadyTakenException("이미 결제 완료된 좌석입니다.");
            }

            kafkaTemplate.send(TOPIC, seatId + ":" + userId);
            idempotencyBucket.set("COMPLETED", 10, TimeUnit.MINUTES);

            log.info("✅ Kafka 결제 이벤트 발행 완료 (seatId={}, userId={})", seatId, userId);

        } catch (Exception e) {
            idempotencyBucket.delete();
            throw e;
        }
    }
}
