package com.dev.ticketing_system.controller.client;

import com.dev.ticketing_system.dto.ApiResponse;
import com.dev.ticketing_system.dto.QueueStatusDto;
import com.dev.ticketing_system.service.CaptchaService;
import com.dev.ticketing_system.service.QueueService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueApiController {

    private final QueueService queueService;
    private final CaptchaService captchaService;

    @GetMapping("/captcha")
    public ResponseEntity<ApiResponse<Map<String, String>>> getCaptcha(HttpSession session) {
        String code = captchaService.generateCaptcha(session.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("captcha", code)));
    }

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<?>> enterQueue(@RequestParam Long concertId,
                                                     @RequestParam String userId,
                                                     @RequestParam String captchaInput,
                                                     HttpSession session) {
        String lookupKey = session.getId();

        if (!captchaService.validateCaptcha(lookupKey, captchaInput)) {
            log.warn("캡차 검증 실패 - userId: {}, input: {}", userId, captchaInput);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("잘못된 보안 문자입니다."));
        }

        queueService.registerQueue(concertId, userId);
        return ResponseEntity.ok(ApiResponse.success("대기열 진입 성공"));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<QueueStatusDto>> getQueueStatus(@RequestParam Long concertId,
                                                                      @RequestParam String userId) {
        QueueStatusDto status = queueService.getQueueStatus(concertId, userId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
