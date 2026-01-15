package com.dev.ticketing_system.config;

import com.dev.ticketing_system.entity.Concert;
import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.repository.ConcertRepository;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitConfig implements CommandLineRunner {

    private final ConcertRepository concertRepository;
    private final JdbcTemplate jdbcTemplate; // JPA 대신 JdbcTemplate 주입

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

            // JDBC Batch를 이용한 고속 저장
            bulkInsertSeats(concert1);
            bulkInsertSeats(concert2);

            log.info(">>> 초기 데이터 공연 등록 완료");
        }
    }

    private void bulkInsertSeats(Concert concert) {
        String sql = "INSERT INTO seats (concert_id, seat_number, status, version) VALUES (?, ?, ?, 0)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setLong(1, concert.getId());
                ps.setInt(2, i + 1); // 좌석 번호 (1부터 시작)
                ps.setString(3, "AVAILABLE"); // SeatStatus enum 문자열
            }

            @Override
            public int getBatchSize() {
                return concert.getTotalSeats();
            }
        });
    }
}