package com.dev.ticketing_system.controller.client;

import com.dev.ticketing_system.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentApiController {

    private final PaymentService paymentService;
    private final RedissonClient redissonClient;

    @PostMapping("/complete")
    public String completePayment(@RequestParam Long seatId, @RequestParam String userId) {
        paymentService.sendPaymentEvent(seatId, userId);
        return "결제 요청이 접수되었습니다. 곧 완료됩니다.";
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancelPayment(@RequestParam Long seatId) {
        String lockKey = "seat:lock:" + seatId;
        RLock lock = redissonClient.getLock(lockKey);

        // forceUnlock은 호출한 쓰레드가 락의 소유자가 아니더라도 락을 해제합니다.
        // 결제 취소 페이지 이탈 등 예외 상황에서 락을 확실히 풀어주기 위해 사용합니다.
        if (lock.isLocked()) {
            lock.forceUnlock();
            redissonClient.getBucket(lockKey + ":user").delete();
        }

        return ResponseEntity.ok("결제가 취소되어 좌석 선점이 해제되었습니다.");
    }
}