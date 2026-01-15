package com.dev.ticketing_system.repository;

import com.dev.ticketing_system.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    // FETCH JOIN을 사용하여 Concert 정보까지 한 번에 가져와 N+1 방지
    @Query("SELECT s FROM Seat s JOIN FETCH s.concert WHERE s.concert.id = :concertId ORDER BY s.seatNumber ASC")
    List<Seat> findByConcertIdOrderBySeatNumberAsc(@Param("concertId") Long concertId);

    long countByConcertId(Long concertId);

    void deleteByConcertId(Long concertId);

    // 전체 좌석 중 특정 상태(SOLD)인 좌석 수 카운트
    // Spring Data JPA는 반환 타입을 int로 지정하면 내부적으로 long 결과를 int로 변환해줍니다.
    int countByStatus(Seat.SeatStatus status);

    // '특정 콘서트'의 판매된 좌석 수만 카운트 (모니터링 시 더 정확함)
    // 예: countByConcertIdAndStatus(1L, SeatStatus.SOLD)
    int countByConcertIdAndStatus(Long concertId, Seat.SeatStatus status);
}