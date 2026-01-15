package com.dev.ticketing_system.service;

import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private final SeatRepository seatRepository;
    private final RedissonClient redissonClient;

    private static final String LOCK_KEY = "seat:lock:";

    @Transactional
    public void confirmPayment(Long seatId, String userId) {
        String lockKey = LOCK_KEY + seatId;
        String userKey = lockKey + ":user";

        RLock lock = redissonClient.getLock(lockKey);
        RBucket<String> userBucket = redissonClient.getBucket(userKey);

        try {
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new IllegalStateException("Seat not found"));

            // ✅ 멱등 처리 (이미 SOLD면 그냥 종료)
            if (seat.getStatus() == Seat.SeatStatus.SOLD) {
                log.info(">>> [Consumer] 이미 SOLD 처리된 좌석 - skip (seatId={})", seatId);
                return;
            }

            seat.markAsSold();
            // save() 불필요 (Dirty Checking)
            log.info(">>> [Consumer] 좌석 SOLD 처리 완료 (DB)");

        } catch (OptimisticLockingFailureException e) {
            // ⭐ 정상적인 중복 처리 상황
            log.warn(">>> [Consumer] Optimistic Lock 충돌 - 이미 처리된 이벤트 (seatId={})", seatId);

        } finally {
            // 🔑 DB 트랜잭션 종료 이후 락 해제
            try {
                userBucket.delete();
                if (lock.isLocked()) {
                    lock.forceUnlock();
                    log.info(">>> [Consumer] Redis 락 해제 완료 (seatId={})", seatId);
                }
            } catch (Exception e) {
                log.warn(">>> [Consumer] Redis 락 해제 실패: {}", e.getMessage());
            }
        }
    }
}
