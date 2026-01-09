package com.dev.ticketing_system.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 공통 에러
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력 값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 에러입니다."),

    // 공연 관련 에러
    CONCERT_NOT_FOUND(HttpStatus.NOT_FOUND, "CON001", "존재하지 않는 공연입니다."),
    ALREADY_REGISTERED_CONCERT(HttpStatus.BAD_REQUEST, "CON002", "이미 등록된 공연 정보입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}