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

        String queueKey = "concert:queue:" + targetId;
        int queueSize = redissonClient.getScoredSortedSet(queueKey).size();

        int soldCount = seatRepository.countByConcertIdAndStatus(targetId, Seat.SeatStatus.SOLD);
        long totalSeats = concert.getTotalSeats();

        long ticketPrice = 100000;
        long totalRevenue = soldCount * ticketPrice;

        double salesRate = (totalSeats > 0) ? ((double) soldCount / totalSeats) * 100 : 0;

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
