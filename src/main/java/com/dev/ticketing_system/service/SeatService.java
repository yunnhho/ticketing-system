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

    @Transactional(readOnly = true)
    public List<SeatResponseDto> getAvailableSeats(Long concertId) {
        String cacheKey = CACHE_KEY_PREFIX + concertId;

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

        List<Seat> seats = seatRepository.findByConcertIdOrderBySeatNumberAsc(concertId);
        List<SeatResponseDto> seatDtos = seats.stream()
                .map(SeatResponseDto::from)
                .collect(Collectors.toList());

        Map<String, SeatResponseDto> seatMap = seatDtos.stream()
                .collect(Collectors.toMap(
                        dto -> String.valueOf(dto.getId()),
                        dto -> dto
                ));

        redisTemplate.opsForHash().putAll(cacheKey, seatMap);
        redisTemplate.expire(cacheKey, Duration.ofMinutes(10));

        return seatDtos;
    }

    private void updateSeatLockStatusInCache(Long concertId, Long seatId, boolean locked) {
        String cacheKey = CACHE_KEY_PREFIX + concertId;
        String hashKey = String.valueOf(seatId);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            SeatResponseDto seatDto = (SeatResponseDto) redisTemplate.opsForHash().get(cacheKey, hashKey);

            if (seatDto != null) {
                seatDto.setLocked(locked);
                redisTemplate.opsForHash().put(cacheKey, hashKey, seatDto);
                log.info("[Cache Update] 좌석 락 변경: seatId={}, locked={}", seatId, locked);
            }
        }
    }

    public void occupySeat(Long seatId, Long concertId, String userId, String lockType) {
        occupyWithRedisson(seatId, concertId, userId);
    }

    @Transactional(readOnly = true)
    public void occupyWithRedisson(Long seatId, Long concertId, String userId) {
        String key = LOCK_KEY + seatId;
        RLock lock = redissonClient.getLock(key);
        boolean isLocked = false;

        try {
            isLocked = lock.tryLock(0, 5, TimeUnit.MINUTES);
            if (!isLocked) {
                throw new SeatAlreadyTakenException("이미 선택된 좌석입니다.");
            }

            validateSeat(seatId, concertId);
            redissonClient.getBucket(key + ":user").set(userId, 5, TimeUnit.MINUTES);
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
