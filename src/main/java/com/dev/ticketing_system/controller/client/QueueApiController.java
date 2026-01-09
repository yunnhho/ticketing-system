package com.dev.ticketing_system.controller.client;

import com.dev.ticketing_system.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueApiController {

    private final QueueService queueService;

    /**
     * 유저의 현재 대기 순번 조회
     * @return 대기 순번 (입장 가능 상태면 -1 반환)
     */
    @GetMapping("/rank")
    public Long getRank(@RequestParam Long concertId, @RequestParam String userId) {
        // 유저가 실제 입장 허용 목록(Active 세션)에 있는지 먼저 확인
        if (queueService.isAllowed(concertId, userId)) {
            return -1L;
        }

        // 아직 대기 중이라면 현재 순위 반환
        return queueService.getRank(concertId, userId);
    }
}