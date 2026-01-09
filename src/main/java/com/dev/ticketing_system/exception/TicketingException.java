package com.dev.ticketing_system.exception;

import lombok.Getter;

@Getter
public class TicketingException extends RuntimeException {

    private final ErrorCode errorCode;

    public TicketingException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public TicketingException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
