package com.dev.ticketing_system.controller.admin;

import com.dev.ticketing_system.dto.ConcertRequest;
import com.dev.ticketing_system.entity.Concert;
import com.dev.ticketing_system.repository.ConcertRepository;
import com.dev.ticketing_system.repository.SeatRepository;
import com.dev.ticketing_system.service.ConcertService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/concerts")
@RequiredArgsConstructor
public class AdminConcertController {

    private final ConcertService concertService;
    private final SeatRepository seatRepository;
    private final ConcertRepository concertRepository;

    // 공연 목록 조회
    @GetMapping
    public String list(Model model) {
        model.addAttribute("concerts", concertRepository.findAll());
        return "admin/concert/list";
    }

    // 공연 등록 폼
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("concertForm", new ConcertRequest());
        return "admin/concert/createForm";
    }

    // 공연 등록 처리
    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("concertForm") ConcertRequest request, BindingResult result) {
        if (result.hasErrors()) {
            return "admin/concert/createForm";
        }
        concertService.save(request);
        return "redirect:/admin/concerts";
    }

    // 상세 조회 (Read Detail)
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Concert concert = concertRepository.findById(id).orElseThrow();
        model.addAttribute("concert", concert);
        model.addAttribute("currentSeatCount", seatRepository.countByConcertId(id));
        return "admin/concert/detail";
    }

    // 수정 폼 (Update Form)
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("concert", concertRepository.findById(id).orElseThrow());
        return "admin/concert/edit";
    }

    // 수정 실행 (Update Action)
    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute Concert concertData) {
        Concert concert = concertRepository.findById(id).orElseThrow();
        // 엔티티의 update 메서드 호출
        concert.update(concertData.getTitle(), concertData.getVenue(), concertData.getTotalSeats());
        concertRepository.save(concert);
        return "redirect:/admin/concerts/" + id;
    }

    // 삭제 실행
    @PostMapping("/{id}/delete")
    @Transactional // 여러 삭제 작업을 하나의 트랜잭션으로 묶음
    public String delete(@PathVariable Long id) {
        seatRepository.deleteByConcertId(id); // 좌석 먼저 삭제
        concertRepository.deleteById(id);    // 공연 삭제
        return "redirect:/admin/concerts";
    }
}
