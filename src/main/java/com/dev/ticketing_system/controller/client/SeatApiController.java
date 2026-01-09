package com.dev.ticketing_system.controller.client;

import com.dev.ticketing_system.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatApiController {

    private final SeatService seatService;

    @GetMapping("/{seatId}/occupy")
    public ResponseEntity<?> occupy(@PathVariable Long seatId, @RequestParam String userId) {
        boolean success = seatService.occupySeat(seatId, userId);
        if (success) {
            return ResponseEntity.ok(Collections.singletonMap("success", true));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Collections.singletonMap("success", false));
        }
    }
}