package com.dev.ticketing_system.controller.client;

import com.dev.ticketing_system.dto.QueueStatusDto;
import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.repository.ConcertRepository;
import com.dev.ticketing_system.repository.SeatRepository;
import com.dev.ticketing_system.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.redisson.api.RedissonClient;

import java.util.List;

@Controller
@RequestMapping("/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final SeatRepository seatRepository;
    private final RedissonClient redissonClient;
    private final ConcertRepository concertRepository;
    private final QueueService queueService;

    @GetMapping("")
    public String index(Model model) {
        model.addAttribute("concerts", concertRepository.findAll());
        return "client/index";
    }

    // 1. 대기열 페이지 (캡차 입력 화면)
    @GetMapping("/{id}/wait")
    public String waitPage(@PathVariable Long id, @RequestParam String userId, Model model) {
        model.addAttribute("concertId", id);
        model.addAttribute("userId", userId);
        // 초기 대기 인원 정보 (캡차 통과 전에도 보여주고 싶을 경우 활용)
        model.addAttribute("waitingCount", queueService.getRank(id, userId));
        return "client/concert/wait";
    }

    // 2. 좌석 선택 페이지 (보안 로직 추가)
    @GetMapping("/{id}/seats")
    public String seatSelectionPage(@PathVariable Long id, @RequestParam String userId, Model model) {
        // [보안] 대기열을 통과하지 않은(isAllowed=false) 유저가 URL로 강제 접속 시 대기 페이지로 튕김
        if (!queueService.isAllowed(id, userId)) {
            return "redirect:/concerts/" + id + "/wait?userId=" + userId;
        }

        List<Seat> seats = seatRepository.findByConcertIdOrderBySeatNumberAsc(id);

        for (Seat seat : seats) {
            String lockKey = "seat:lock:" + seat.getId();
            if (redissonClient.getBucket(lockKey).isExists()) {
                seat.setTemporaryStatus("OCCUPIED");
            }
        }

        model.addAttribute("concertId", id);
        model.addAttribute("userId", userId);
        model.addAttribute("seats", seats);
        return "client/concert/seats";
    }

    // 3. 결제 페이지
    @GetMapping("/{id}/payments")
    public String paymentPage(@PathVariable Long id,
                              @RequestParam Long seatId,
                              @RequestParam String userId,
                              Model model) {
        model.addAttribute("concertId", id);
        model.addAttribute("seatId", seatId);
        model.addAttribute("userId", userId);
        return "client/concert/payments";
    }

    @GetMapping("/success")
    public String successPage() {
        return "client/concert/success";
    }

    // 4. 대기 상태 확인 API (JS에서 3초마다 호출)
    @GetMapping("/{id}/status")
    @ResponseBody
    public QueueStatusDto getQueueStatus(@PathVariable Long id, @RequestParam String userId) {
        // QueueService에서 계산된 순번, 예상시간, 입장여부를 한 번에 가져옴
        return queueService.getQueueStatus(id, userId);
    }
}