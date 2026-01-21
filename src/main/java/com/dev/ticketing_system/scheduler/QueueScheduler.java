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

    /**
     * 3초마다 대기열에서 유저를 추출하여 입장 허용 상태로 변경
     */
    @Scheduled(fixedDelay = 3000)
    public void processQueue() {
        List<Concert> concerts = concertRepository.findAll();

        if (concerts.isEmpty()) return;

        for (Concert concert : concerts) {
            //  allowEntry가 "입장된 유저들의 토큰(또는 ID) 목록"을 반환해야 함
            Set<String> enteredTokens = queueService.allowEntry(concert.getId(), 100);

            if (enteredTokens != null && !enteredTokens.isEmpty()) {
                for (String token : enteredTokens) {
                    String userId = token;

                    log.info("WebSocket 알림 발송: user={}", userId);

                    // 해당 유저에게만 {"pass": true} 메시지 전송
                    messagingTemplate.convertAndSend("/topic/queue/" + userId, Map.of("pass", true));
                }
            }
        }
    }
}