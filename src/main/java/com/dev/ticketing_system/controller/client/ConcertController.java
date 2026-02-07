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

    @GetMapping("/{id}/wait")
    public String waitPage(@PathVariable Long id, @RequestParam String userId, Model model) {
        model.addAttribute("concertId", id);
        model.addAttribute("userId", userId);
        model.addAttribute("waitingCount", queueService.getRank(id, userId));
        return "client/concert/wait";
    }

    @GetMapping("/{id}/seats")
    public String seatSelectionPage(@PathVariable Long id, @RequestParam String userId, Model model) {
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

    @GetMapping("/{id}/status")
    @ResponseBody
    public QueueStatusDto getQueueStatus(@PathVariable Long id, @RequestParam String userId) {
        return queueService.getQueueStatus(id, userId);
    }
}
