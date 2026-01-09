package com.dev.ticketing_system.config;

import com.dev.ticketing_system.entity.Concert;
import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.repository.ConcertRepository;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitConfig implements CommandLineRunner {

    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;

    @Override
    @Transactional // 좌석 대량 저장을 위해 트랜잭션 처리
    public void run(String... args) {
        if (concertRepository.count() == 0) {
            log.info(">>> 초기 데이터 생성 시작...");

            // 1. 공연 생성 및 저장
            Concert concert1 = new Concert("2026 월드 투어 - 서울", "서울 올림픽 주경기장", 500);
            Concert concert2 = new Concert("윈터 재즈 페스티벌", "예술의 전당", 300);

            concertRepository.save(concert1);
            concertRepository.save(concert2);

            // 2. 각 공연에 맞는 좌석 생성
            createSeatsForConcert(concert1);
            createSeatsForConcert(concert2);

            log.info(">>> 공연 2건 및 좌석 {}개 등록 완료", seatRepository.count());
        }
    }

    private void createSeatsForConcert(Concert concert) {
        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= concert.getTotalSeats(); i++) {
            seats.add(new Seat(concert, i)); // Seat 엔티티의 생성자(Concert, seatNumber) 사용
        }
        seatRepository.saveAll(seats); // 벌크 인서트로 성능 최적화
    }
}