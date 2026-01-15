package com.dev.ticketing_system.exception;

public class SeatAlreadyTakenException extends RuntimeException {
    public SeatAlreadyTakenException(String message) {
        super(message);
    }
}