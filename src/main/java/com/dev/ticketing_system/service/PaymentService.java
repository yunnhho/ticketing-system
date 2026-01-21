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

            // [디버깅 로그] Redis 상태 확인 (이 로그를 꼭 확인하세요!)
            boolean isLocked = lock.isLocked();
            String ownerId = userBucket.get();

            // 혹시 JSON 직렬화 때문에 따옴표가 붙었을 수 있으므로 제거 처리
            if (ownerId != null) {
                ownerId = ownerId.replace("\"", "").trim();
            }

            log.info("🔍 [결제 검증] seatId={}, userId(요청)={}, isLocked={}, ownerId(Redis)={}",
                    seatId, userId, isLocked, ownerId);

            // A. 락 존재 여부 확인
            if (!isLocked) {
                log.warn("❌ 결제 실패: 락이 존재하지 않음 (시간 초과 또는 선점 안됨)");
                throw new SeatAlreadyTakenException("결제 시간이 초과되었거나 선점 정보가 없습니다.");
            }

            // B. 락 주인 확인 (내가 맞는지)
            if (ownerId == null || !ownerId.equals(userId)) {
                log.warn("❌ 결제 실패: 락 주인 불일치. 요청자={}, 주인={}", userId, ownerId);
                throw new SeatAlreadyTakenException("좌석 점유 정보가 일치하지 않습니다. (다른 사람이 선점함)");
            }

            // ⭐️ 3. DB 상태 검증
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 좌석입니다."));

            if (seat.getStatus() == Seat.SeatStatus.SOLD) {
                throw new SeatAlreadyTakenException("이미 결제 완료된 좌석입니다.");
            }

            // ⭐️ 4. Kafka 발행
            kafkaTemplate.send(TOPIC, seatId + ":" + userId);

            // ⭐️ 5. 멱등성 완료 처리
            idempotencyBucket.set("COMPLETED", 10, TimeUnit.MINUTES);

            log.info("✅ Kafka 결제 이벤트 발행 완료 (seatId={}, userId={})", seatId, userId);

        } catch (Exception e) {
            // 예외 발생 시 멱등성 키 삭제 (재시도 가능하도록)
            idempotencyBucket.delete();
            throw e;
        }
    }
}