package com.dev.ticketing_system.service;

import com.dev.ticketing_system.dto.ConcertRequestDto;
import com.dev.ticketing_system.entity.Concert;
import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.repository.ConcertRepository;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
     * ⭐️ Cacheable: "concerts"라는 이름의 캐시에서 Key가 'all'인 데이터를 찾음.
     * 없으면 DB 조회 후 저장, 있으면 DB 스킵하고 캐시 반환.
     */
    @Cacheable(value = "concerts", key = "'all'")
    public List<Concert> findAll() {
        return concertRepository.findAll();
    }

    /**
     * 새로운 공연 등록
     * ⭐️ CacheEvict: 데이터가 변경되었으므로 "concerts" 캐시를 삭제하여 갱신 유도.
     */
    @Transactional
    @CacheEvict(value = "concerts", key = "'all'")
    public void save(ConcertRequestDto request) {
        Concert concert = new Concert(
                request.getTitle(),
                request.getVenue(),
                request.getTotalSeats()
        );
        concertRepository.save(concert);

        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= request.getTotalSeats(); i++) {
            seats.add(new Seat(concert, i));
        }
        seatRepository.saveAll(seats);
    }
}