package com.dev.ticketing_system.service;

import com.dev.ticketing_system.dto.ConcertDashboardDto;
import com.dev.ticketing_system.entity.Concert;
import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final SeatRepository seatRepository;
    private final RedissonClient redissonClient;

    public ConcertDashboardDto getDashboardStats(Concert concert) {
        Long targetId = concert.getId();

        // 1. 대기열 조회 (Redis)
        String queueKey = "concert:queue:" + targetId;
        int queueSize = redissonClient.getScoredSortedSet(queueKey).size();

        // 2. 판매량 조회 (DB)
        int soldCount = seatRepository.countByConcertIdAndStatus(targetId, Seat.SeatStatus.SOLD);
        long totalSeats = concert.getTotalSeats();

        // 3. 매출액 계산
        long ticketPrice = 100000;
        long totalRevenue = soldCount * ticketPrice;

        // 4. 예매율 계산
        double salesRate = (totalSeats > 0) ? ((double) soldCount / totalSeats) * 100 : 0;

        // 5. DTO 변환 및 반환
        return ConcertDashboardDto.builder()
                .concertId(concert.getId())
                .title(concert.getTitle())
                .queueSize(queueSize)
                .soldCount(soldCount)
                .totalSeats(totalSeats)
                .totalRevenue(NumberFormat.getInstance(Locale.KOREA).format(totalRevenue))
                .salesRate(String.format("%.1f", salesRate))
                .build();
    }
}