package com.dev.ticketing_system.service;

import com.dev.ticketing_system.dto.QueueStatusDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @InjectMocks
    private QueueService queueService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RScoredSortedSet<String> scoredSortedSet;

    @Mock
    private RBucket<Object> rBucket;

    @Test
    @DisplayName("대기열 등록 테스트")
    @SuppressWarnings("unchecked")
    void registerQueue_Success() {
        // Given
        Long concertId = 1L;
        String userId = "user1";
        String key = "concert:queue:" + concertId;

        when(redissonClient.getScoredSortedSet(eq(key))).thenReturn((RScoredSortedSet) scoredSortedSet);
        when(scoredSortedSet.getScore(userId)).thenReturn(null);
        when(scoredSortedSet.rank(userId)).thenReturn(5);

        // When
        Long rank = queueService.registerQueue(concertId, userId);

        // Then
        assertEquals(5L, rank);
        verify(scoredSortedSet).add(anyDouble(), eq(userId));
    }

    @Test
    @DisplayName("입장 허용 로직 테스트")
    @SuppressWarnings("unchecked")
    void allowEntry_Success() {
        // Given
        Long concertId = 1L;
        int count = 10;
        String userId = "user1";
        String queueKey = "concert:queue:" + concertId;
        String activeKey = "concert:active:" + concertId + ":" + userId;

        when(redissonClient.getScoredSortedSet(eq(queueKey))).thenReturn((RScoredSortedSet) scoredSortedSet);
        when(scoredSortedSet.valueRange(0, count - 1)).thenReturn(Collections.singletonList(userId));
        when(redissonClient.getBucket(activeKey)).thenReturn(rBucket);

        // When
        Set<String> result = queueService.allowEntry(concertId, count);

        // Then
        assertTrue(result.contains(userId));
        verify(rBucket).set(eq("true"), any(Duration.class));
        verify(scoredSortedSet).remove(userId);
    }

    @Test
    @DisplayName("대기 상태 조회 테스트")
    @SuppressWarnings("unchecked")
    void getQueueStatus_Success() {
        // Given
        Long concertId = 1L;
        String userId = "user1";
        String key = "concert:queue:" + concertId;

        when(redissonClient.getScoredSortedSet(eq(key))).thenReturn((RScoredSortedSet) scoredSortedSet);
        when(scoredSortedSet.rank(userId)).thenReturn(10);

        // When
        QueueStatusDto status = queueService.getQueueStatus(concertId, userId);

        // Then
        assertEquals(11L, status.getRank());
        assertFalse(status.isPass());
    }
}