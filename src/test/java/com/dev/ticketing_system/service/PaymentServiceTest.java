package com.dev.ticketing_system.service;

import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.exception.SeatAlreadyTakenException;
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
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private RBucket<String> rBucket;

    @Mock
    private RLock rLock;

    @Test
    @DisplayName("결제 검증 및 Kafka 전송 성공 테스트")
    @SuppressWarnings("unchecked")
    void validateAndPay_Success() {
        // Given
        Long seatId = 1L;
        String userId = "user1";
        String idempotencyKey = "key123";

        when(redissonClient.getBucket(contains("idempotency:"))).thenReturn((RBucket) rBucket);
        when(rBucket.isExists()).thenReturn(false);

        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.isLocked()).thenReturn(true);
        when(redissonClient.getBucket(contains(":user"))).thenReturn((RBucket) rBucket);
        when(rBucket.get()).thenReturn(userId);

        Seat seat = mock(Seat.class);
        when(seatRepository.findById(seatId)).thenReturn(Optional.of(seat));
        when(seat.getStatus()).thenReturn(Seat.SeatStatus.AVAILABLE);

        // When
        paymentService.validateAndPay(seatId, userId, idempotencyKey);

        // Then
        verify(kafkaTemplate).send(eq("payment-completed"), anyString());
        verify(rBucket).set(eq("COMPLETED"), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("락 소유자 불일치 시 예외 발생 테스트")
    @SuppressWarnings("unchecked")
    void validateAndPay_OwnerMismatch() {
        // Given
        Long seatId = 1L;
        String userId = "user1";
        String ownerId = "otherUser";

        when(redissonClient.getBucket(contains("idempotency:"))).thenReturn((RBucket) rBucket);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.isLocked()).thenReturn(true);
        when(redissonClient.getBucket(contains(":user"))).thenReturn((RBucket) rBucket);
        when(rBucket.get()).thenReturn(ownerId);

        // When & Then
        assertThrows(SeatAlreadyTakenException.class, () -> 
            paymentService.validateAndPay(seatId, userId, "key123")
        );
    }
}