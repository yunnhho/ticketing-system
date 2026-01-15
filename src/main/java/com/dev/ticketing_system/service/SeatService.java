package com.dev.ticketing_system.service;

import com.dev.ticketing_system.entity.Seat;
import com.dev.ticketing_system.exception.SeatAlreadyTakenException;
import com.dev.ticketing_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository; // DB 조회를 위해 추가
    private final RedissonClient redissonClient;

    private static final String LOCK_KEY = "seat:lock:";

    /**
     * 좌석 선점 (Redis Lock + DB 검증)
     * 성공 시: Redis 락을 유지한 상태로 리턴 (PaymentService에서 해제)
     * 실패 시: 예외 발생 및 락 즉시 해제
     */
    @Transactional(readOnly = true)
    public void occupySeat(Long seatId, Long concertId, String userId) {
        String key = LOCK_KEY + seatId;
        RLock lock = redissonClient.getLock(key);

        boolean isLocked = false;

        try {
            // 1. Redis 분산 락 시도 (WaitTime 0: 대기 없이 즉시 실패 처리)
            isLocked = lock.tryLock(0, 5, TimeUnit.MINUTES);

            if (!isLocked) {
                throw new SeatAlreadyTakenException("이미 선택된 좌석입니다. (락 선점 중)");
            }

            // 2. DB 검증 (락을 획득한 이후에 수행해야 안전함)
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 좌석입니다."));

            // ⭐️ [핵심 수정] 요청한 콘서트의 좌석이 맞는지 검증 (JMeter 테스트 오류 방지)
            if (!seat.getConcert().getId().equals(concertId)) {
                log.warn("잘못된 접근 감지: SeatId {}는 Concert {} 소속이나, 요청은 Concert {}로 들어옴",
                        seatId, seat.getConcert().getId(), concertId);
                throw new IllegalArgumentException("해당 콘서트의 좌석이 아닙니다.");
            }

            // 3. 이미 팔린 좌석인지 확인
            if (seat.getStatus() == Seat.SeatStatus.SOLD) {
                throw new SeatAlreadyTakenException("이미 판매 완료된 좌석입니다.");
            }

            // 4. Redis에 점유자 정보 저장 (PaymentService 검증용)
            redissonClient.getBucket(key + ":user")
                    .set(userId, 5, TimeUnit.MINUTES);

            log.info("좌석 선점 성공: SeatId {}, UserId {}", seatId, userId);

            // ⚠️ 여기서 lock.unlock()을 하지 않습니다!
            // 결제가 끝나거나 취소될 때까지 락을 유지해야 합니다.

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("서버 에러 발생");

        } catch (Exception e) {
            // ⭐️ [중요] DB 검증 등에서 예외가 터지면, 잡았던 락을 풀어줘야 함
            // 그렇지 않으면 5분 동안 아무도 이 좌석을 못 건드림 (데드락 방지)
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            throw e; // 예외를 다시 던져서 컨트롤러가 알게 함
        }
    }
}