package com.dev.ticketing_system.consumer;

import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConsumer {

    private final SeatRepository seatRepository;
    private final RedissonClient redissonClient;

    @Transactional
    @KafkaListener(topics = "payment-completed", groupId = "ticketing-group")
    public void consume(String message) {
        log.info("Kafka 결제 이벤트 수신: {}", message);

        String[] data = message.split(":");
        Long seatId = Long.parseLong(data[0]);
        String userId = data[1];

        // 1. 좌석 조회 (N+1 방지를 위해 필요시 fetch join 레포지토리 메서드 사용 가능)
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new RuntimeException("좌석을 찾을 수 없습니다."));

        // [멱등성] 이미 SOLD라면 무시 (중복 메시지 처리)
        if (seat.getStatus() == Seat.SeatStatus.SOLD) {
            log.warn("이미 SOLD 처리된 좌석입니다. SeatId: {}", seatId);
            return;
        }

        try {
            // 2. DB 상태 변경 및 낙관적 락 체크
            seat.markAsSold();
            seatRepository.saveAndFlush(seat); // 즉시 반영하여 버전 충돌 확인

            log.info("DB 상태 변경 완료 (SOLD) - SeatId: {}", seatId);

        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("낙관적 락 충돌! 이미 다른 요청에 의해 결제 완료됨. SeatId: {}", seatId);
            return;
        }

        // 3. Redis 분산 락 해제 (강제 해제)
        String lockKey = "seat:lock:" + seatId;
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isLocked()) {
            lock.forceUnlock();
            // 유저 정보 버킷도 삭제
            redissonClient.getBucket(lockKey + ":user").delete();
        }

        log.info("결제 및 자원 해제 최종 성공 - SeatId: {}, UserId: {}", seatId, userId);
    }
}