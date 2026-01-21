package com.dev.ticketing_system.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice // 모든 컨트롤러의 예외를 여기서 가로챕니다.
public class GlobalExceptionHandler {

    @ExceptionHandler(SeatAlreadyTakenException.class)
    public ResponseEntity<String> handleSeatTaken(SeatAlreadyTakenException e) {
        log.warn("좌석 선점 실패: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    /**
     * 비즈니스 로직 중 발생하는 커스텀 예외 처리
     */
    @ExceptionHandler(TicketingException.class)
    public String handleTicketingException(TicketingException e, Model model) {
        log.error("TicketingException: {}", e.getMessage());
        model.addAttribute("errorMessage", e.getMessage());
        return "admin/error/400"; // 공통 에러 페이지로 이동
    }

    /**
     * 예상치 못한 서버 내부 에러 처리
     */
    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        // favicon.ico 요청 누락은 로그만 남기고 에러 페이지로 보내지 않음
        if (e instanceof org.springframework.web.servlet.resource.NoResourceFoundException) {
            log.debug("정적 리소스가 존재하지 않음: {}", e.getMessage());
            return null; // 빈 응답을 보내 브라우저가 조용히 넘어가게 함
        }

        log.error("Internal Server Error: ", e);
        model.addAttribute("errorMessage", "시스템 내부 에러가 발생했습니다.");
        return "admin/error/500";
    }
}