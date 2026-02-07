package com.dev.ticketing_system.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SeatAlreadyTakenException.class)
    public ResponseEntity<String> handleSeatTaken(SeatAlreadyTakenException e) {
        log.warn("좌석 선점 실패: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(TicketingException.class)
    public String handleTicketingException(TicketingException e, Model model) {
        log.error("TicketingException: {}", e.getMessage());
        model.addAttribute("errorMessage", e.getMessage());
        return "admin/error/400";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        if (e instanceof org.springframework.web.servlet.resource.NoResourceFoundException) {
            log.debug("정적 리소스가 존재하지 않음: {}", e.getMessage());
            return null;
        }

        log.error("Internal Server Error: ", e);
        model.addAttribute("errorMessage", "시스템 내부 에러가 발생했습니다.");
        return "admin/error/500";
    }
}
