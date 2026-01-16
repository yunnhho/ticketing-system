package com.dev.ticketing_system.controller.client;

import com.dev.ticketing_system.dto.ApiResponse;
import com.dev.ticketing_system.exception.SeatAlreadyTakenException;
import com.dev.ticketing_system.repository.SeatRepository;
import com.dev.ticketing_system.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final RedissonClient redissonClient;

    /**
     * ✅ 1. 결제 승인 요청 (멱등성 키 지원)
     */
    @PostMapping("/payment/process")
    public ResponseEntity<ApiResponse<?>> processPayment(@RequestParam Long seatId,
                                                         @RequestParam String userId,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        // 키가 없으면 생성 (약한 멱등성 보장)
        String finalKey = (idempotencyKey != null) ? idempotencyKey : "NO_KEY_" + seatId + "_" + userId + "_" + System.currentTimeMillis();

        log.info("API 결제 요청 진입: seatId={}, userId={}, idemKey={}", seatId, userId, finalKey);

        try {
            paymentService.validateAndPay(seatId, userId, finalKey);

            return ResponseEntity.ok(
                    ApiResponse.success(Map.of("message", "결제 요청이 접수되었습니다.", "status", "success"))
            );

        } catch (IllegalArgumentException e) {
            // 멱등성 중복 요청 등
            log.warn("잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));

        } catch (SeatAlreadyTakenException e) {
            log.warn("이미 선점된 좌석: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("결제 시스템 에러", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("시스템 오류가 발생했습니다."));
        }
    }

    /**
     * ✅ 2. 결제 취소 요청
     */
    @PostMapping("/api/payments/cancel")
    public ResponseEntity<ApiResponse<?>> cancelPayment(@RequestParam Long seatId) {
        String lockKey = "seat:lock:" + seatId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.isLocked()) {
                lock.forceUnlock();
                log.info("결제 취소로 인한 락 해제 완료: seatId={}", seatId);
            }
            return ResponseEntity.ok(ApiResponse.success("결제가 취소되어 좌석 선점이 해제되었습니다."));

        } catch (Exception e) {
            log.error("락 해제 중 오류 발생", e);
            return ResponseEntity.ok(ApiResponse.error("이미 해제되었거나 오류가 발생했습니다."));
        }
    }
}