package com.dev.ticketing_system.controller.client;

import com.dev.ticketing_system.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatApiController {

    private final SeatService seatService;

    @PostMapping("/{seatId}/occupy")
    public ResponseEntity<?> occupy(@PathVariable Long seatId,
                                    @RequestParam Long concertId,
                                    @RequestParam String userId) {
        // 예외가 발생하면 GlobalExceptionHandler가 409 Conflict를 리턴함
        // 따라서 여기서는 성공 로직만 작성하면 됨
        seatService.occupySeat(seatId, concertId, userId);

        return ResponseEntity.ok(Collections.singletonMap("success", true));
    }
}