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
        // 1. 멱등성 검사
        String idemKey = "idempotency:" + idempotencyKey;
        RBucket<String> idempotencyBucket = redissonClient.getBucket(idemKey);

        if (idempotencyBucket.isExists() && !"PROCESSING".equals(idempotencyBucket.get())) {
            throw new IllegalArgumentException("이미 완료된 요청입니다.");
        }
        idempotencyBucket.set("PROCESSING", 10, TimeUnit.MINUTES);

        try {
            // ⭐️ 2. 락 검증 로직 (여기가 문제의 원인일 확률 99%)
            String lockKey = LOCK_KEY + seatId;
            String userKey = lockKey + ":user";

            RLock lock = redissonClient.getLock(lockKey);
            RBucket<String> userBucket = redissonClient.getBucket(userKey);

            /*
            혹시 JSON 직렬화 때문에 따옴표가 붙었을 수 있으므로 제거 처리
            if (ownerId != null) {
                ownerId = ownerId.replace("\"", "").trim();
            }

            [디버깅 로그] Redis 상태 확인 (이 로그를 꼭 확인하세요!)
            log.info("🔍 [결제 검증] seatId={}, userId(요청)={}, isLocked={}, ownerId(Redis)={}",
                    seatId, userId, isLocked, ownerId);
            */

            // 1. 락 존재 여부 확인
            if (!lock.isLocked()) {
                throw new SeatAlreadyTakenException("결제 시간이 초과되어 좌석 선점이 해제되었습니다.");
            }

            // 2. Redis에 저장된 소유자 ID 가져오기
            String ownerId = userBucket.get();

            // 3. 소유자 검증
            if (ownerId == null || !ownerId.equals(userId)) {
                log.warn("락 소유자 불일치: 요청={}, 실제={}", userId, ownerId);
                throw new SeatAlreadyTakenException("좌석 점유 권한이 없습니다. (다른 유저가 선점 중)");
            }

            // 4. DB 상태 검증
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 좌석입니다."));

            if (seat.getStatus() == Seat.SeatStatus.SOLD) {
                throw new SeatAlreadyTakenException("이미 결제 완료된 좌석입니다.");
            }

            // 5. Kafka 발행
            kafkaTemplate.send(TOPIC, seatId + ":" + userId);

            // 6. 멱등성 완료 처리
            idempotencyBucket.set("COMPLETED", 10, TimeUnit.MINUTES);

            log.info("✅ Kafka 결제 이벤트 발행 완료 (seatId={}, userId={})", seatId, userId);

        } catch (Exception e) {
            idempotencyBucket.delete();
            throw e;
        }
    }
}