package com.dev.ticketing_system.controller.client;

import com.dev.ticketing_system.exception.SeatAlreadyTakenException;
import com.dev.ticketing_system.repository.SeatRepository;
import com.dev.ticketing_system.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final RedissonClient redissonClient;
    private final SeatRepository seatRepository;

    /**
     * ✅ 1. 결제 승인 요청 (API 방식)
     * - 화면 리다이렉트(String) 대신 ResponseEntity(JSON)를 반환합니다.
     * - 프론트엔드(JS)에서 fetch로 요청을 보내고, 응답 코드(200, 409 등)에 따라 화면을 이동시킵니다.
     */
    @PostMapping("/payment/process")
    @ResponseBody // ⭐️ 중요: ViewResolver를 타지 않고 데이터를 바로 반환함
    public ResponseEntity<?> processPayment(@RequestParam Long seatId,
                                            @RequestParam String userId) {
        log.info("API 결제 요청 진입: seatId={}, userId={}", seatId, userId);

        try {
            // 서비스 로직 실행 (검증 및 Kafka 발행)
            paymentService.validateAndPay(seatId, userId);

            // 성공 시 200 OK와 함께 성공 메시지 전달
            return ResponseEntity.ok(Map.of("message", "결제 요청이 접수되었습니다.", "status", "success"));

        } catch (SeatAlreadyTakenException e) {
            log.warn("이미 선점된 좌석: {}", e.getMessage());

            // 실패 시 409 Conflict 상태 코드 반환
            // 프론트에서 이 코드를 보고 에러 페이지로 보낼지 결정함
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "이미 결제된 좌석입니다.", "error", "taken"));
        } catch (Exception e) {
            log.error("결제 시스템 에러", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "시스템 오류가 발생했습니다."));
        }
    }

    /**
     * ✅ 2. 결제 취소 요청 (API 방식)
     * - 기존 코드 유지 (이미 잘 작성되어 있습니다)
     */
    @PostMapping("/api/payments/cancel")
    @ResponseBody // ⭐️ 얘도 JSON 반환
    public ResponseEntity<?> cancelPayment(@RequestParam Long seatId) {
        String lockKey = "seat:lock:" + seatId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락이 걸려있고, 현재 스레드(또는 점유자)가 잡고 있는지 확인 후 해제하는 것이 정석이나,
            // 관리자나 시스템에 의한 강제 해제 로직이라면 forceUnlock도 사용 가능
            if (lock.isLocked()) {
                lock.forceUnlock();
                log.info("결제 취소로 인한 락 해제 완료: seatId={}", seatId);
            }
            return ResponseEntity.ok("결제가 취소되어 좌석 선점이 해제되었습니다.");

        } catch (Exception e) {
            log.error("락 해제 중 오류 발생", e);
            return ResponseEntity.ok("이미 해제되었거나 오류가 발생했습니다."); // 에러를 굳이 사용자에게 보여줄 필요 없으면 200 처리
        }
    }
}