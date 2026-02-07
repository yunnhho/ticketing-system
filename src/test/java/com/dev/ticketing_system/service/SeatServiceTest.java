package com.dev.ticketing_system.service;

import com.dev.ticketing_system.entity.Concert;
import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.repository.SeatRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @InjectMocks
    private SeatService seatService;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RLock rLock;

    @Mock
    private RBucket<Object> rBucket;

    @Test
    @DisplayName("좌석 점유 성공 테스트 (Redisson)")
    void occupySeat_Success() throws InterruptedException {
        // Given
        Long seatId = 1L;
        Long concertId = 1L;
        String userId = "user1";
        String lockKey = "seat:lock:" + seatId;
        String activeKey = lockKey + ":user";

        // Redisson Mocks
        when(redissonClient.getLock(lockKey)).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(redissonClient.getBucket(activeKey)).thenReturn(rBucket);

        // Repository Mocks
        Seat seat = mock(Seat.class);
        Concert concert = mock(Concert.class);
        when(seatRepository.findById(seatId)).thenReturn(Optional.of(seat));
        when(seat.getConcert()).thenReturn(concert);
        when(concert.getId()).thenReturn(concertId);
        when(seat.getStatus()).thenReturn(Seat.SeatStatus.AVAILABLE);

        // RedisTemplate Mock (Assume cache miss to avoid opsForHash mocking complexity for this specific test)
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // When
        seatService.occupySeat(seatId, concertId, userId, "redisson");

        // Then
        verify(rLock).tryLock(0, 5, TimeUnit.MINUTES); // 락 시도 확인
        verify(rBucket).set(userId, 5, TimeUnit.MINUTES); // 점유자 정보 저장 확인
        verify(seatRepository).findById(seatId); // DB 조회 확인
    }
}
