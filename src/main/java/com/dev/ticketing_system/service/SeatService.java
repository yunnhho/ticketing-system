package com.dev.ticketing_system.service;

import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.exception.SeatAlreadyTakenException;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate; // [추가] 캐싱용

    private static final String LOCK_KEY = "seat:lock:";
    private static final String CACHE_KEY_PREFIX = "seats:available:concert:"; // [추가] 캐시 키 접두사

    // ----------------------------------------------------------------
    // 1. 좌석 조회 (Redis Caching 적용 - Look Aside 패턴)
    // ----------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<SeatSimpleDto> getAvailableSeats(Long concertId) {
        String cacheKey = CACHE_KEY_PREFIX + concertId;

        // 1. Redis 캐시 확인
        try {
            List<SeatSimpleDto> cachedSeats = (List<SeatSimpleDto>) redisTemplate.opsForValue().get(cacheKey);
            if (cachedSeats != null && !cachedSeats.isEmpty()) {
                log.info("[Cache Hit] Redis에서 좌석 정보 조회: concertId={}", concertId);
                return cachedSeats;
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 실패 (DB에서 조회 시도): {}", e.getMessage());
        }

        // 2. 캐시 없으면 DB 조회
        List<Seat> seats = seatRepository.findByConcertIdAndStatus(concertId, Seat.SeatStatus.AVAILABLE);

        // Entity -> DTO 변환
        List<SeatSimpleDto> seatDtos = seats.stream()
                .map(SeatSimpleDto::from)
                .collect(Collectors.toList());

        // 3. Redis에 저장 (TTL 1분)
        try {
            redisTemplate.opsForValue().set(cacheKey, seatDtos, Duration.ofMinutes(1));
            log.info("[Cache Miss] DB 조회 후 Redis 저장 완료: concertId={}", concertId);
        } catch (Exception e) {
            log.warn("Redis 캐시 저장 실패: {}", e.getMessage());
        }

        return seatDtos;
    }

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

    // 기본 전략: Redis 분산 락
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

            // 좌석 상태가 변했으므로 캐시 삭제 (Cache Eviction)
            evictCache(concertId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("서버 에러");
        } catch (Exception e) {
            if (isLocked && lock.isHeldByCurrentThread()) lock.unlock();
            throw e;
        }
    }

    // 비교 전략 1: DB 비관적 락 (SELECT ... FOR UPDATE)
    @Transactional
    public void occupyWithPessimisticLock(Long seatId, Long concertId, String userId) {
        validateSeat(seatId, concertId);
        log.info("DB Pessimistic Lock 선점 성공: SeatId {}", seatId);
        // DB 락 사용 시에도 캐시 정합성을 위해 삭제 필요
        evictCache(concertId);
    }

    // 비교 전략 2: Java Synchronized (단일 서버용)
    public synchronized void occupyWithSynchronized(Long seatId, Long concertId, String userId) {
        validateSeat(seatId, concertId);
        log.info("Synchronized 선점 성공: SeatId {}", seatId);
        evictCache(concertId);
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

    // 캐시 무효화 메서드
    private void evictCache(Long concertId) {
        String cacheKey = CACHE_KEY_PREFIX + concertId;
        redisTemplate.delete(cacheKey);
        log.info("[Cache Evict] 좌석 상태 변경으로 캐시 삭제: {}", cacheKey);
    }

    // 내부용 간단 DTO (따로 파일 만들 필요 없음)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatSimpleDto implements Serializable {
        private Long seatId;
        private int seatNumber;
        private String status;

        public static SeatSimpleDto from(Seat seat) {
            return new SeatSimpleDto(
                    seat.getId(),
                    seat.getSeatNumber(),
                    seat.getStatus().name()
            );
        }
    }
}