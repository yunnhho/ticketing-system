package com.dev.ticketing_system.controller.client;

import com.dev.ticketing_system.dto.QueueStatusDto;
import com.dev.ticketing_system.service.CaptchaService;
import com.dev.ticketing_system.service.QueueService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueApiController {

    private final QueueService queueService;
    private final CaptchaService captchaService;

    // [신규] 1. 캡차 생성 API (대기열 진입 전 호출)
    @GetMapping("/captcha")
    public ResponseEntity<Map<String, String>> getCaptcha(HttpSession session) {
        // 세션 ID 기반으로 캡차 생성 후 Redis 저장
        String code = captchaService.generateCaptcha(session.getId());
        return ResponseEntity.ok(Map.of("captcha", code));
    }

    // [수정] 2. 대기열 진입 (캡차 검증 포함)
    @PostMapping("/token")
    public ResponseEntity<?> enterQueue(@RequestParam Long concertId,
                                        @RequestParam String userId,
                                        @RequestParam String captchaInput, // 캡차 입력값
                                        HttpSession session) {
        // [수정] 세션 ID 혹은 userId를 기반으로 검증 (JMeter 테스트 시 세션 유지가 어려울 수 있음)
        String lookupKey = session.getId();

        // 만약 테스트 중이라 세션이 없다면 userId를 키로 사용하거나 마스터 키 체크
        if (!captchaService.validateCaptcha(lookupKey, captchaInput)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("잘못된 보안 문자입니다.");
        }

        queueService.registerQueue(concertId, userId);
        return ResponseEntity.ok("대기열 진입 성공");
    }

    // [수정] 3. 대기 상태 조회 (순번 + 예상 대기 시간)
    // 기존 getRank 대신 QueueStatusDto를 반환하여 예상 시간까지 제공
    @GetMapping("/status")
    public ResponseEntity<QueueStatusDto> getQueueStatus(@RequestParam Long concertId,
                                                         @RequestParam String userId) {
        QueueStatusDto status = queueService.getQueueStatus(concertId, userId);
        return ResponseEntity.ok(status);
    }
}