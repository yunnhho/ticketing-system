package com.dev.ticketing_system.controller.admin;

import com.dev.ticketing_system.dto.ConcertDashboardDto;
import com.dev.ticketing_system.entity.Concert;
import com.dev.ticketing_system.repository.ConcertRepository;
import com.dev.ticketing_system.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final ConcertRepository concertRepository;
    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public String dashboard(Model model, @RequestParam(required = false) Long concertId) {
        List<Concert> concerts = concertRepository.findAll();
        model.addAttribute("concerts", concerts);

        if (concerts.isEmpty()) {
            return "admin/dashboard";
        }

        Concert targetConcert = (concertId == null)
                ? concerts.get(0)
                : concertRepository.findById(concertId).orElse(concerts.get(0));

        ConcertDashboardDto dashboardDto = adminDashboardService.getDashboardStats(targetConcert);

        model.addAttribute("selectedConcert", targetConcert);
        model.addAttribute("stats", dashboardDto);

        return "admin/dashboard";
    }
}
