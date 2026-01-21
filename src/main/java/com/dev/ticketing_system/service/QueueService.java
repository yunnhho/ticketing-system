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
     * @return 현재 내 앞에 대기 중인 인원 수
     */
    public Long registerQueue(Long concertId, String userId) {
        String key = QUEUE_KEY + concertId;
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(key);

        // 이미 있으면 기존 순위 반환, 없으면 추가
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
        // rank가 null이면 이미 대기열을 통과해 ACTIVE로 갔거나 없는 유저임
        // null이 아니면 인덱스이므로 +1을 해서 실제 대기 순번을 체감하게 함
        return (rank != null) ? rank.longValue() + 1 : 0L;
    }

    /**
     * 대기열에서 상위 N명을 꺼내 입장 허용 목록으로 이동 (TTL 적용)
     */
    /**
     * 대기열에서 상위 N명을 추출하여 입장 허용 (Active 상태로 전환)
     * @return 입장 성공한 유저 ID 목록 (WebSocket 알림용)
     */
    public Set<String> allowEntry(Long concertId, int count) {
        String queueKey = QUEUE_KEY + concertId;
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(queueKey);

        // 1. 대기열 상위 count명 추출
        Collection<String> usersToAllow = queue.valueRange(0, count - 1);

        // 반환할 유저 목록을 담을 Set 생성
        Set<String> enteredUsers = new HashSet<>();

        // 대기자가 없으면 빈 Set 반환
        if (usersToAllow.isEmpty()) {
            return enteredUsers;
        }

        for (String userId : usersToAllow) {
            // 개별 유저 입장을 위한 고유 키 생성
            String userActiveKey = ACTIVE_KEY + concertId + ":" + userId;

            // 2. 유저별 입장 권한 부여 및 10분 뒤 자동 삭제(TTL) 설정
            // (이 시간이 지나면 좌석 선택 페이지 접근이 다시 차단됩니다.)
            redissonClient.getBucket(userActiveKey).set("true", Duration.ofMinutes(10));

            // 3. 대기열(Queue)에서 삭제
            queue.remove(userId);

            log.info("User [{}] 입장 허용 (Concert: {}, 10분간 유효)", userId, concertId);

            // 4. 결과 목록에 추가
            enteredUsers.add(userId);
        }

        // 5. 입장한 유저 목록 반환 -> Scheduler가 받아서 WebSocket 알림 전송
        return enteredUsers;
    }

    /**
     * 입장 허용된 유저인지 확인 (TTL이 적용된 키가 존재하는지 체크)
     */
    public boolean isAllowed(Long concertId, String userId) {
        String userActiveKey = ACTIVE_KEY + concertId + ":" + userId;
        // 키가 존재하면(만료되지 않았으면) true 반환
        return redissonClient.getBucket(userActiveKey).isExists();
    }

    public QueueStatusDto getQueueStatus(Long concertId, String userId) {
        String key = QUEUE_KEY + concertId;
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(key);

        Integer rank = queue.rank(userId);

        if (rank == null) {
            // 이미 입장했거나 없는 유저
            return new QueueStatusDto(0L, 0L, true);
        }

        long myRank = rank + 1; // 0부터 시작하므로 +1
        long estimatedSeconds = (long) (myRank / THROUGHTPUT_PER_SECOND);

        return new QueueStatusDto(myRank, estimatedSeconds, false);
    }
}