package com.dev.ticketing_system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final RedissonClient redissonClient;
    private static final String LOCK_KEY = "seat:lock:";

    public boolean occupySeat(Long seatId, String userId) {
        String key = LOCK_KEY + seatId;
        RLock lock = redissonClient.getLock(key);
        try {
            // waitTime 0: 이미 누가 잡고 있으면 바로 실패 반환
            // leaseTime 5분: 결제 안하고 잠수 타면 5분 뒤 자동 해제
            if (lock.tryLock(0, 5, TimeUnit.MINUTES)) {
                redissonClient.getBucket(key + ":user").set(userId, 5, TimeUnit.MINUTES);
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 주의: 여기서 unlock을 해버리면 좌석 선택 직후 락이 풀립니다.
        // 임시 점유(선점)이므로, 결제 완료 시점 혹은 취소 시점에 해제 로직을 명확히 분리해야 합니다.
        return false;
    }

}