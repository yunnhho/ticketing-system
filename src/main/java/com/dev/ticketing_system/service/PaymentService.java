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
        // ⭐️ 1. 멱등성 검사 (Idempotency Check)
        // 네트워크 타임아웃 등으로 인해 클라이언트가 재요청을 보냈을 때, 중복 결제를 방지함
        String idemKey = "idempotency:" + idempotencyKey;
        RBucket<String> idempotencyBucket = redissonClient.getBucket(idemKey);

        if (idempotencyBucket.isExists()) {
            throw new IllegalArgumentException("이미 처리 중이거나 완료된 요청입니다. (중복 결제 방지)");
        }

        // 처리 시작 표시 (10분간 유효)
        idempotencyBucket.set("PROCESSING", 10, TimeUnit.MINUTES);

        try {
            // ⭐️ 2. 락 검증 로직
            String lockKey = LOCK_KEY + seatId;
            String userKey = lockKey + ":user";

            RLock lock = redissonClient.getLock(lockKey);
            RBucket<String> userBucket = redissonClient.getBucket(userKey);

            // 락이 풀려있거나(시간 초과), 내가 잡은 락이 아니면 실패
            if (!lock.isLocked()) {
                throw new SeatAlreadyTakenException("결제 시간이 초과되었습니다.");
            }

            String ownerId = userBucket.get();
            if (ownerId == null || !ownerId.equals(userId)) {
                throw new SeatAlreadyTakenException("좌석 점유 정보가 일치하지 않습니다.");
            }

            // ⭐️ 3. DB 상태 검증
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 좌석입니다."));

            if (seat.getStatus() == Seat.SeatStatus.SOLD) {
                throw new SeatAlreadyTakenException("이미 결제 완료된 좌석입니다.");
            }

            // ⭐️ 4. Kafka Write-Behind (비동기 처리)
            kafkaTemplate.send(TOPIC, seatId + ":" + userId);

            // ⭐️ 5. 멱등성 키 상태 완료로 변경
            idempotencyBucket.set("COMPLETED", 10, TimeUnit.MINUTES);

            log.info(">>> Kafka 결제 이벤트 발행 완료 (seatId={}, userId={})", seatId, userId);

        } catch (Exception e) {
            // 예외 발생 시 멱등성 키 삭제 (재시도 가능하도록)
            idempotencyBucket.delete();
            throw e;
        }
    }
}