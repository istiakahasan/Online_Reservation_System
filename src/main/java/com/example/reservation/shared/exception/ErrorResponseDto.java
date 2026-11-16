package com.example.reservation.shared.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Standard API error response envelope")
public record ErrorResponseDto(
        @Schema(description = "HTTP status code", example = "400")
        int status,

        @Schema(description = "Application error code", example = "VALIDATION_FAILED")
        String errorCode,

        @Schema(description = "Human-readable error message", example = "Invalid request payload")
        String message,

        @Schema(description = "Timestamp when error occurred")
        Instant timestamp,

        @Schema(description = "Field-level validation error details, if any")
        Map<String, String> details
) {
    public ErrorResponseDto(int status, String errorCode, String message) {
        this(status, errorCode, message, Instant.now(), null);
    }

    public ErrorResponseDto(int status, String errorCode, String message, Map<String, String> details) {
        this(status, errorCode, message, Instant.now(), details);
    }
}
