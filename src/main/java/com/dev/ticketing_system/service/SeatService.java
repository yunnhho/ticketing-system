package com.dev.ticketing_system.service;

import com.dev.ticketing_system.dto.SeatResponseDto;
import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.exception.SeatAlreadyTakenException;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOCK_KEY = "seat:lock:";
    private static final String CACHE_KEY_PREFIX = "seats:concert:";

    /**
     * 1. 좌석 조회 (DTO의 isLocked 활용)
     */
    @Transactional(readOnly = true)
    public List<SeatResponseDto> getAvailableSeats(Long concertId) {
        String cacheKey = CACHE_KEY_PREFIX + concertId;

        // A. Redis 캐시 확인
        try {
            List<Object> cachedData = redisTemplate.opsForHash().values(cacheKey);
            if (!cachedData.isEmpty()) {
                return cachedData.stream()
                        .map(obj -> (SeatResponseDto) obj)
                        .sorted(Comparator.comparingInt(SeatResponseDto::getSeatNumber))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 실패: {}", e.getMessage());
        }

        // B. DB 조회
        List<Seat> seats = seatRepository.findByConcertIdOrderBySeatNumberAsc(concertId);
        List<SeatResponseDto> seatDtos = seats.stream()
                .map(SeatResponseDto::from)
                .collect(Collectors.toList());

        // C. Redis 저장 (Hash)
        Map<String, SeatResponseDto> seatMap = seatDtos.stream()
                .collect(Collectors.toMap(
                        dto -> String.valueOf(dto.getId()),
                        dto -> dto
                ));

        redisTemplate.opsForHash().putAll(cacheKey, seatMap);
        redisTemplate.expire(cacheKey, Duration.ofMinutes(10));

        return seatDtos;
    }

    /**
     * 2. 캐시 내 특정 좌석 잠금 상태 업데이트 (boolean 변경)
     */
    private void updateSeatLockStatusInCache(Long concertId, Long seatId, boolean locked) {
        String cacheKey = CACHE_KEY_PREFIX + concertId;
        String hashKey = String.valueOf(seatId);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            SeatResponseDto seatDto = (SeatResponseDto) redisTemplate.opsForHash().get(cacheKey, hashKey);

            if (seatDto != null) {
                // ✅ DTO의 isLocked 필드만 변경
                seatDto.setLocked(locked);

                redisTemplate.opsForHash().put(cacheKey, hashKey, seatDto);
                log.info("[Cache Update] 좌석 락 변경: seatId={}, locked={}", seatId, locked);
            }
        }
    }

    /**
     * 좌석 선점 (Redisson)
     */
    public void occupySeat(Long seatId, Long concertId, String userId, String lockType) {
        occupyWithRedisson(seatId, concertId, userId);
    }

    @Transactional(readOnly = true)
    public void occupyWithRedisson(Long seatId, Long concertId, String userId) {
        String key = LOCK_KEY + seatId;
        RLock lock = redissonClient.getLock(key);
        boolean isLocked = false;

        try {
            // 락 획득 (5분 점유)
            isLocked = lock.tryLock(0, 5, TimeUnit.MINUTES);
            if (!isLocked) {
                throw new SeatAlreadyTakenException("이미 선택된 좌석입니다.");
            }

            validateSeat(seatId, concertId);

            // Redis 점유자 정보 저장
            redissonClient.getBucket(key + ":user").set(userId, 5, TimeUnit.MINUTES);

            // ✅ 캐시 업데이트: isLocked = true 로 변경
            updateSeatLockStatusInCache(concertId, seatId, true);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("서버 에러");
        } catch (Exception e) {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            throw e;
        }
    }

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