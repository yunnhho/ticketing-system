package com.dev.ticketing_system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ConcertRequest {

    @NotBlank(message = "공연 제목은 필수입니다.")
    private String title;

    @NotBlank(message = "공연 장소는 필수입니다.")
    private String venue;

    @Min(value = 10, message = "좌석은 최소 10개 이상 등록해야 합니다.")
    private int totalSeats;
}