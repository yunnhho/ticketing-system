package com.dev.ticketing_system.scheduler;

import com.dev.ticketing_system.entity.Concert;
import com.dev.ticketing_system.repository.ConcertRepository;
import com.dev.ticketing_system.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final QueueService queueService;
    private final ConcertRepository concertRepository;

    /**
     * 1초마다 대기열에서 유저를 추출하여 입장 허용 상태로 변경
     * 대규모 트래픽 시 DB 부하를 고려하여 초당 처리량을 조절합니다.
     */
    @Scheduled(fixedDelay = 3000)
    public void processQueue() {
        // 만약 공연이 매우 많아진다면 페이징 처리를 고려해야 함
        List<Concert> concerts = concertRepository.findAll();

        if (concerts.isEmpty()) return;

        for (Concert concert : concerts) {
            // Redis 연산을 일괄 처리(Pipeline)하면 더 좋지만, 현재는 각 공연별로 호출
            queueService.allowEntry(concert.getId(), 50);
        }
    }
}