package com.dev.ticketing_system.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConcertDashboardDto {
    private Long concertId;
    private String title;

    private int queueSize;      // 대기 인원
    private int soldCount;      // 판매된 좌석 수
    private long totalSeats;    // 전체 좌석 수

    private String totalRevenue; // 포맷팅된 매출액 (예: "100,000,000")
    private String salesRate;    // 포맷팅된 예매율 (예: "85.5")
}