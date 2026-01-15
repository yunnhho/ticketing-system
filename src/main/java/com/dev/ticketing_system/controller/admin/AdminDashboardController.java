package com.dev.ticketing_system.controller.admin;

import com.dev.ticketing_system.entity.Concert;
import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.repository.ConcertRepository;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;
    private final RedissonClient redissonClient;

    @GetMapping
    public String dashboard(Model model, @RequestParam(required = false) Long concertId) {
        // 1. 드롭다운용 전체 목록 조회
        List<Concert> concerts = concertRepository.findAll();
        model.addAttribute("concerts", concerts);

        // 데이터가 없으면 빈 화면 처리
        if (concerts.isEmpty()) {
            return "admin/dashboard";
        }

        // 2. 타겟 콘서트 설정 (파라미터 없으면 첫 번째)
        Concert targetConcert = (concertId == null)
                ? concerts.get(0)
                : concertRepository.findById(concertId).orElse(concerts.get(0));

        Long targetId = targetConcert.getId();

        // 3. 지표 계산
        String queueKey = "concert:queue:" + targetId;
        int queueSize = redissonClient.getScoredSortedSet(queueKey).size();
        int soldCount = seatRepository.countByConcertIdAndStatus(targetId, Seat.SeatStatus.SOLD);
        long totalSeats = targetConcert.getTotalSeats();
        long ticketPrice = 100000;
        long totalRevenue = soldCount * ticketPrice;
        double salesRate = (totalSeats > 0) ? ((double) soldCount / totalSeats) * 100 : 0;

        // 4. 모델 담기
        model.addAttribute("selectedConcert", targetConcert);
        model.addAttribute("queueSize", queueSize);
        model.addAttribute("soldCount", soldCount);
        model.addAttribute("totalSeats", totalSeats);
        model.addAttribute("totalRevenue", NumberFormat.getInstance(Locale.KOREA).format(totalRevenue));
        model.addAttribute("salesRate", String.format("%.1f", salesRate));

        return "admin/dashboard";
    }
}