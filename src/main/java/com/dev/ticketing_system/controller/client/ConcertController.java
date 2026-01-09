package com.dev.ticketing_system.controller.client;

import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.repository.ConcertRepository;
import com.dev.ticketing_system.repository.SeatRepository;
import com.dev.ticketing_system.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.redisson.api.RedissonClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final SeatRepository seatRepository;
    private final RedissonClient redissonClient;
    private final ConcertRepository concertRepository;
    private final QueueService queueService;


    @GetMapping("") // 메인 페이지 (공연 목록)
    public String index(Model model) {
        model.addAttribute("concerts", concertRepository.findAll());
        return "client/index";
    }

    @GetMapping("/{id}/wait")
    public String waitPage(@PathVariable Long id, @RequestParam String userId, Model model) {
        // 대기열 등록 로직 수행 후 화면 반환
        long waitingCount = queueService.registerQueue(id, userId);
        model.addAttribute("concertId", id);
        model.addAttribute("userId", userId);
        model.addAttribute("waitingCount", waitingCount);
        return "client/concert/wait";
    }

    @GetMapping("/{id}/seats")
    public String seatSelectionPage(@PathVariable Long id, @RequestParam String userId, Model model) {
        List<Seat> seats = seatRepository.findByConcertIdOrderBySeatNumberAsc(id);

        // 각 좌석별로 Redis에 점유 키가 있는지 확인하여 임시 상태 부여
        for (Seat seat : seats) {
            String lockKey = "seat:lock:" + seat.getId();
            // Redis에 키가 존재한다면 (다른 유저가 결제 중이라면)
            if (redissonClient.getBucket(lockKey).isExists()) {
                // DB 상태가 AVAILABLE이더라도 화면에는 OCCUPIED로 보이게 처리
                // 별도의 DTO를 쓰거나 Seat 엔티티에 @Transient 필드를 활용할 수 있습니다.
                seat.setTemporaryStatus("OCCUPIED");
            }
        }

        model.addAttribute("concertId", id);
        model.addAttribute("userId", userId);
        model.addAttribute("seats", seats);

        return "client/concert/seats";
    }

    @GetMapping("/{id}/payments")
    public String paymentPage(@PathVariable Long id,
                              @RequestParam Long seatId,
                              @RequestParam String userId,
                              Model model) {
        model.addAttribute("concertId", id);
        model.addAttribute("seatId", seatId);
        model.addAttribute("userId", userId);
        return "client/concert/payments"; // 위에서 만든 HTML
    }

    @GetMapping("/success")
    public String successPage() {
        return "client/concert/success"; // 최종 성공 페이지
    }

    @GetMapping("/{id}/status")
    @ResponseBody // <-- 자바스크립트가 JSON으로 읽을 수 있게 반드시 추가!
    public Map<String, Object> getQueueStatus(@PathVariable Long id, @RequestParam String userId) {
        Map<String, Object> response = new HashMap<>();

        // 1. 입장 허용 여부 체크 (아까 만든 TTL 적용 버전)
        boolean isAllowed = queueService.isAllowed(id, userId);

        // 2. 대기 순번 체크
        long queueSize = queueService.getRank(id, userId);

        response.put("isAllowed", isAllowed);
        response.put("queueSize", isAllowed ? 0 : queueSize);

        return response; // 스프링이 { "isAllowed": false, "queueSize": 5 } 형식의 JSON으로 자동 변환함
    }

}