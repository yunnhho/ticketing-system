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

    /**
     * [신규] 1. 캡차 생성 API
     * Return: ApiResponse<Map<String, String>>
     */
    @GetMapping("/captcha")
    public ResponseEntity<ApiResponse<Map<String, String>>> getCaptcha(HttpSession session) {
        String code = captchaService.generateCaptcha(session.getId());

        // [200 OK] { success: true, data: { "captcha": "AB12CD" } }
        return ResponseEntity.ok(
                ApiResponse.success(Map.of("captcha", code))
        );
    }

    /**
     * [수정] 2. 대기열 진입 (캡차 검증 포함)
     * Return: ApiResponse<?> (성공 시 메시지만, 실패 시 에러 메시지)
     */
    @PostMapping("/token")
    public ResponseEntity<ApiResponse<?>> enterQueue(@RequestParam Long concertId,
                                                     @RequestParam String userId,
                                                     @RequestParam String captchaInput,
                                                     HttpSession session) {
        String lookupKey = session.getId();

        // 캡차 검증 실패 시
        if (!captchaService.validateCaptcha(lookupKey, captchaInput)) {
            log.warn("캡차 검증 실패 - userId: {}, input: {}", userId, captchaInput);

            // [400 Bad Request] { success: false, message: "잘못된 보안 문자입니다." }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("잘못된 보안 문자입니다."));
        }

        // 대기열 등록
        queueService.registerQueue(concertId, userId);

        // [200 OK] { success: true, message: "대기열 진입 성공" }
        return ResponseEntity.ok(ApiResponse.success("대기열 진입 성공"));
    }

    /**
     * [수정] 3. 대기 상태 조회
     * Return: ApiResponse<QueueStatusDto>
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<QueueStatusDto>> getQueueStatus(@RequestParam Long concertId,
                                                                      @RequestParam String userId) {
        QueueStatusDto status = queueService.getQueueStatus(concertId, userId);

        // [200 OK] { success: true, data: { rank: 10, estimatedSeconds: 30, ... } }
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}