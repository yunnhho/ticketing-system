package com.dev.ticketing_system.repository;

import com.dev.ticketing_system.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    // FETCH JOIN을 사용하여 Concert 정보까지 한 번에 가져와 N+1 방지
    @Query("SELECT s FROM Seat s JOIN FETCH s.concert WHERE s.concert.id = :concertId ORDER BY s.seatNumber ASC")
    List<Seat> findByConcertIdOrderBySeatNumberAsc(Long concertId);
    long countByConcertId(Long concertId);
    void deleteByConcertId(Long concertId);
}