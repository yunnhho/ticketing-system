package com.dev.ticketing_system.scheduler;

import com.dev.ticketing_system.entity.Concert;
import com.dev.ticketing_system.repository.ConcertRepository;
import com.dev.ticketing_system.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final QueueService queueService;
    private final ConcertRepository concertRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelay = 3000)
    public void processQueue() {
        List<Concert> concerts = concertRepository.findAll();
        if (concerts.isEmpty()) return;

        for (Concert concert : concerts) {
            Set<String> enteredTokens = queueService.allowEntry(concert.getId(), 100);

            if (enteredTokens != null && !enteredTokens.isEmpty()) {
                for (String userId : enteredTokens) {
                    log.info("WebSocket 알림 발송: user={}", userId);
                    messagingTemplate.convertAndSend("/topic/queue/" + userId, Map.of("pass", true));
                }
            }
        }
    }
}
