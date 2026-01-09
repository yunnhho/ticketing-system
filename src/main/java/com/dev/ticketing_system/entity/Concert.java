package com.dev.ticketing_system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "공연명은 필수입니다.")
    private String title;

    @NotBlank(message = "공연 장소는 필수입니다.")
    private String venue;

    @Min(value = 0, message = "총 좌석은 0개 이상이어야 합니다.")
    private int totalSeats;

    public Concert(String title, String venue, int totalSeats) {
        this.title = title;
        this.venue = venue;
        this.totalSeats = totalSeats;
    }

    // 비즈니스 로직: 정보 수정
    public void update(String title, String venue, int totalSeats) {
        this.title = title;
        this.venue = venue;
        this.totalSeats = totalSeats;
    }
}