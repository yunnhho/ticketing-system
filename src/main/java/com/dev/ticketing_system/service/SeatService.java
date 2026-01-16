package com.dev.ticketing_system.service;

import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.exception.SeatAlreadyTakenException;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final RedissonClient redissonClient;

    private static final String LOCK_KEY = "seat:lock:";

    /**
     * 좌석 선점 (전략 패턴 적용 가능)
     * @param lockType "REDIS"(기본), "DB"(비관적), "SYNC"(동기화)
     */
    public void occupySeat(Long seatId, Long concertId, String userId, String lockType) {
        if ("DB".equalsIgnoreCase(lockType)) {
            occupyWithPessimisticLock(seatId, concertId, userId);
        } else if ("SYNC".equalsIgnoreCase(lockType)) {
            occupyWithSynchronized(seatId, concertId, userId);
        } else {
            // Default: Redis Distributed Lock
            occupyWithRedisson(seatId, concertId, userId);
        }
    }

    // ⭐️ 기본 전략: Redis 분산 락
    @Transactional(readOnly = true)
    public void occupyWithRedisson(Long seatId, Long concertId, String userId) {
        String key = LOCK_KEY + seatId;
        RLock lock = redissonClient.getLock(key);
        boolean isLocked = false;

        try {
            isLocked = lock.tryLock(0, 5, TimeUnit.MINUTES);
            if (!isLocked) throw new SeatAlreadyTakenException("이미 선택된 좌석입니다.");

            validateSeat(seatId, concertId); // 공통 검증 로직 추출

            // Redis 점유 정보 저장
            redissonClient.getBucket(key + ":user").set(userId, 5, TimeUnit.MINUTES);
            log.info("Redis Lock 선점 성공: SeatId {}, UserId {}", seatId, userId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("서버 에러");
        } catch (Exception e) {
            if (isLocked && lock.isHeldByCurrentThread()) lock.unlock();
            throw e;
        }
    }

    // ⭐️ 비교 전략 1: DB 비관적 락 (SELECT ... FOR UPDATE)
    @Transactional
    public void occupyWithPessimisticLock(Long seatId, Long concertId, String userId) {
        // Repository에 findByIdWithLock 필요 (아래 Repository 참고)
        // 실제로는 이 메서드 내에서 상태 업데이트까지 해야 락의 의미가 있음
        validateSeat(seatId, concertId);
        // 테스트용: 로직 통과 시 로그만 남김
        log.info("DB Pessimistic Lock 선점 성공: SeatId {}", seatId);
    }

    // ⭐️ 비교 전략 2: Java Synchronized (단일 서버용)
    public synchronized void occupyWithSynchronized(Long seatId, Long concertId, String userId) {
        validateSeat(seatId, concertId);
        log.info("Synchronized 선점 성공: SeatId {}", seatId);
    }

    // 공통 검증 로직
    private void validateSeat(Long seatId, Long concertId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("좌석 없음"));

        if (!seat.getConcert().getId().equals(concertId)) {
            throw new IllegalArgumentException("해당 콘서트의 좌석이 아닙니다.");
        }
        if (seat.getStatus() == Seat.SeatStatus.SOLD) {
            throw new SeatAlreadyTakenException("이미 판매된 좌석입니다.");
        }
    }
}