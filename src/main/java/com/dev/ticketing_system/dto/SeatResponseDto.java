package com.dev.ticketing_system.dto;

import com.dev.ticketing_system.entity.Seat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponseDto implements Serializable {
    // Redis 캐싱을 위해 Serializable 구현 권장

    private Long id;
    private int seatNumber;
    private String status; // "AVAILABLE", "SOLD" (DB 상태)

    // Redis 락 여부 (컨트롤러/서비스에서 주입)
    private boolean isLocked;

    /**
     * Entity -> DTO 변환 메서드
     */
    public static SeatResponseDto from(Seat seat) {
        return SeatResponseDto.builder()
                .id(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .status(seat.getStatus().name())
                .isLocked(false) // 기본값 false, 필요 시 Service에서 true로 세팅
                .build();
    }

    /**
     * ✅ 화면 로직: DB 상태와 Redis 락 상태를 조합하여 최종 상태 반환
     * 타임리프에서 seat.displayStatus 로 접근 가능
     */
    public String getDisplayStatus() {
        if ("SOLD".equals(this.status)) {
            return "SOLD";
        }
        if (this.isLocked) {
            return "OCCUPIED"; // 누군가 결제 중 (Redis 락)
        }
        return "AVAILABLE";
    }

    // 락 상태 변경용 Setter (필요시 사용)
    public void setLocked(boolean locked) {
        this.isLocked = locked;
    }
}