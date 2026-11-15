package com.example.reservation.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base abstract exception for all application and domain exceptions.
 * Aligns with the project conventions in CLAUDE.md.
 */
public abstract class AppException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    protected AppException(String message, String errorCode, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    protected AppException(String message, Throwable cause, String errorCode, HttpStatus status) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

