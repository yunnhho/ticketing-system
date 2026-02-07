package com.dev.ticketing_system.service;

import com.dev.ticketing_system.dto.QueueStatusDto;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedissonClient redissonClient;
    private static final String QUEUE_KEY = "concert:queue:";
    private static final String ACTIVE_KEY = "concert:active:";
    private static final double THROUGHTPUT_PER_SECOND = 50.0 / 3.0;

    /**
     * 대기열 참가
     */
    public Long registerQueue(Long concertId, String userId) {
        String key = QUEUE_KEY + concertId;
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(key);

        Double score = queue.getScore(userId);
        if (score == null) {
            queue.add(System.currentTimeMillis(), userId);
        }

        Integer rank = queue.rank(userId);
        return (rank != null) ? rank.longValue() : 0L;
    }

    /**
     * 내 순서 확인
     */
    public Long getRank(Long concertId, String userId) {
        String key = QUEUE_KEY + concertId;
        Integer rank = redissonClient.getScoredSortedSet(key).rank(userId);
        return (rank != null) ? rank.longValue() + 1 : 0L;
    }

    /**
     * 대기열에서 상위 N명을 추출하여 입장 허용 (Active 상태로 전환)
     */
    public Set<String> allowEntry(Long concertId, int count) {
        String queueKey = QUEUE_KEY + concertId;
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(queueKey);

        Collection<String> usersToAllow = queue.valueRange(0, count - 1);
        Set<String> enteredUsers = new HashSet<>();

        if (usersToAllow.isEmpty()) {
            return enteredUsers;
        }

        for (String userId : usersToAllow) {
            String userActiveKey = ACTIVE_KEY + concertId + ":" + userId;
            redissonClient.getBucket(userActiveKey).set("true", Duration.ofMinutes(10));
            queue.remove(userId);

            log.info("User [{}] 입장 허용 (Concert: {}, 10분간 유효)", userId, concertId);
            enteredUsers.add(userId);
        }

        return enteredUsers;
    }

    /**
     * 입장 허용된 유저인지 확인 (TTL이 적용된 키가 존재하는지 체크)
     */
    public boolean isAllowed(Long concertId, String userId) {
        String userActiveKey = ACTIVE_KEY + concertId + ":" + userId;
        return redissonClient.getBucket(userActiveKey).isExists();
    }

    public QueueStatusDto getQueueStatus(Long concertId, String userId) {
        String key = QUEUE_KEY + concertId;
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(key);

        Integer rank = queue.rank(userId);

        if (rank == null) {
            return new QueueStatusDto(0L, 0L, true);
        }

        long myRank = rank + 1;
        long estimatedSeconds = (long) (myRank / THROUGHTPUT_PER_SECOND);

        return new QueueStatusDto(myRank, estimatedSeconds, false);
    }
}
