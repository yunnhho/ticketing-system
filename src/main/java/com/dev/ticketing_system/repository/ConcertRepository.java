package com.dev.ticketing_system.repository;

import com.dev.ticketing_system.entity.Concert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConcertRepository extends JpaRepository<Concert, Long> {

    /**
     * 공연 제목으로 중복 여부를 확인하기 위한 메서드
     * 대형 프로젝트에서는 중복 데이터 방지가 필수입니다.
     */
    boolean existsByTitle(String title);

    // 모든 공연을 가져오되, 관련 데이터가 있다면 한 번에 조회하도록 최적화 가능
    @Query("SELECT DISTINCT c FROM Concert c")
    List<Concert> findAllOptimized();

    /**
     * 제목으로 공연 상세 정보를 조회할 때 사용
     */
    Optional<Concert> findByTitle(String title);
}