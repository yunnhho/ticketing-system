package com.dev.ticketing_system.service;

import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.repository.SeatRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final MeterRegistry meterRegistry;
    private final RedissonClient redissonClient;
    private final SeatRepository seatRepository;

    // 실시간 값을 담을 원자적 변수들
    private AtomicInteger waitingQueueSize = new AtomicInteger(0);
    private AtomicInteger soldSeatCount = new AtomicInteger(0);
    private AtomicInteger activeUserCount = new AtomicInteger(0);

    private static final String QUEUE_KEY = "concert:queue:1"; // 콘서트 ID 1번 기준
    private static final String ACTIVE_KEY_PATTERN = "concert:active:1:*";

    @PostConstruct
    public void init() {
        // 1. 대기열 크기 메트릭 등록
        Gauge.builder("custom.ticket.queue.size", waitingQueueSize, AtomicInteger::get)
                .description("Current waiting queue size")
                .register(meterRegistry);

        // 2. 판매된 좌석 수 메트릭 등록
        Gauge.builder("custom.ticket.sold.count", soldSeatCount, AtomicInteger::get)
                .description("Total sold seats count")
                .register(meterRegistry);

        // 3. 활성 유저(입장 중) 수 메트릭 등록
        Gauge.builder("custom.ticket.active.users", activeUserCount, AtomicInteger::get)
                .description("Current active users processing payment")
                .register(meterRegistry);
    }

    // 5초마다 Redis와 DB를 조회하여 메트릭 갱신 (부하 방지)
    @Scheduled(fixedRate = 5000)
    public void updateMetrics() {
        // Redis 대기열 조회
        int queueSize = redissonClient.getScoredSortedSet(QUEUE_KEY).size();
        waitingQueueSize.set(queueSize);

        // Redis 활성 유저 조회 (keys는 무거울 수 있으니 운영에선 scan 추천, 여기선 간단히 구현)
        int activeCount = (int) redissonClient.getKeys().countExists(ACTIVE_KEY_PATTERN);
        // *Redisson의 countExists는 패턴 매칭이 제한적이므로,
        // 실제로는 Active Set을 따로 관리하거나 간단히 0으로 두고 대기열/판매량에 집중해도 됨.
        // 여기서는 예시로 queueSize와 soldCount에 집중하겠습니다.

        // DB 판매량 조회
        int soldCount = seatRepository.countByStatus(Seat.SeatStatus.SOLD);
        soldSeatCount.set(soldCount);
    }
}