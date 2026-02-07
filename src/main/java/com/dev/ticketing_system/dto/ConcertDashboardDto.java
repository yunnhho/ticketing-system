package com.dev.ticketing_system.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConcertDashboardDto {
    private Long concertId;
    private String title;
    private int queueSize;
    private int soldCount;
    private long totalSeats;
    private String totalRevenue;
    private String salesRate;
}
