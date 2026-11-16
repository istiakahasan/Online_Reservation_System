package com.example.reservation.reservation.domain.exception;

import com.example.reservation.shared.exception.AppException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;

public class CancellationWindowExpiredException extends AppException {

    public CancellationWindowExpiredException(Instant startTime, Instant cancellationTime, Duration window) {
        super(
                String.format("Cancellation window of %d hour(s) before start time (%s) has passed. Current time: %s",
                        window.toHours(), startTime, cancellationTime),
                "CANCELLATION_WINDOW_EXPIRED",
                HttpStatus.CONFLICT
        );
    }

    public CancellationWindowExpiredException(String message) {
        super(message, "CANCELLATION_WINDOW_EXPIRED", HttpStatus.CONFLICT);
    }
}
