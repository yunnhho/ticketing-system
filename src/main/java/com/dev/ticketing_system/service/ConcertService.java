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

    @Cacheable(value = "concerts", key = "'all'")
    public List<Concert> findAll() {
        return concertRepository.findAll();
    }

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

    @Transactional
    public void deleteConcert(Long concertId) {
        seatRepository.deleteByConcertId(concertId);
        concertRepository.deleteById(concertId);
    }
}
