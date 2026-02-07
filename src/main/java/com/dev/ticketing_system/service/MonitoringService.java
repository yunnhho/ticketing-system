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

    private AtomicInteger waitingQueueSize = new AtomicInteger(0);
    private AtomicInteger soldSeatCount = new AtomicInteger(0);
    private AtomicInteger activeUserCount = new AtomicInteger(0);

    private static final String QUEUE_KEY = "concert:queue:1";
    private static final String ACTIVE_KEY_PATTERN = "concert:active:1:*";

    @PostConstruct
    public void init() {
        Gauge.builder("custom.ticket.queue.size", waitingQueueSize, AtomicInteger::get)
                .description("Current waiting queue size")
                .register(meterRegistry);

        Gauge.builder("custom.ticket.sold.count", soldSeatCount, AtomicInteger::get)
                .description("Total sold seats count")
                .register(meterRegistry);

        Gauge.builder("custom.ticket.active.users", activeUserCount, AtomicInteger::get)
                .description("Current active users processing payment")
                .register(meterRegistry);
    }

    @Scheduled(fixedRate = 5000)
    public void updateMetrics() {
        int queueSize = redissonClient.getScoredSortedSet(QUEUE_KEY).size();
        waitingQueueSize.set(queueSize);

        int activeCount = (int) redissonClient.getKeys().countExists(ACTIVE_KEY_PATTERN);
        activeUserCount.set(activeCount);

        int soldCount = seatRepository.countByStatus(Seat.SeatStatus.SOLD);
        soldSeatCount.set(soldCount);
    }
}
