package com.dev.ticketing_system.controller.client;

import com.dev.ticketing_system.dto.ApiResponse;
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
    public ResponseEntity<ApiResponse<?>> occupy(@PathVariable Long seatId,
                                 @RequestParam Long concertId,
                                 @RequestParam String userId,
                                 @RequestParam(defaultValue = "REDIS") String lockType) {
        seatService.occupySeat(seatId, concertId, userId, lockType);
        return ResponseEntity.ok(ApiResponse.success(Collections.singletonMap("success", true)));
    }
}
