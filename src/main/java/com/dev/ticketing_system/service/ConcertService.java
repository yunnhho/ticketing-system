package com.dev.ticketing_system.service;

import com.dev.ticketing_system.dto.ConcertRequest;
import com.dev.ticketing_system.entity.Concert;
import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.repository.ConcertRepository;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ConcertService {

    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;

    /**
     * 전체 공연 목록 조회
     */
    public List<Concert> findAll() {
        return concertRepository.findAll();
    }

    /**
     * 새로운 공연 등록
     */
    @Transactional
    public void save(ConcertRequest request) {
        // 1. 공연 정보 저장
        Concert concert = new Concert(
                request.getTitle(),
                request.getVenue(),
                request.getTotalSeats()
        );
        concertRepository.save(concert);

        // 2. 좌석 대량 생성 로직 (Bulk Generation)
        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= request.getTotalSeats(); i++) {
            seats.add(new Seat(concert, i));
        }

        // 리스트를 한 번에 저장 (내부적으로 배치 처리 권장)
        seatRepository.saveAll(seats);
    }
}