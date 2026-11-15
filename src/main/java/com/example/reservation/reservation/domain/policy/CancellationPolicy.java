package com.example.reservation.reservation.domain.policy;

import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.exception.CancellationWindowExpiredException;

import java.time.Duration;
import java.time.Instant;

public interface CancellationPolicy {

    /**
     * Checks if cancellation is allowed at the given timestamp with respect to the cancellation window.
     */
    boolean isCancellationAllowed(ReservationEntity reservation, Instant cancellationTime, Duration cancellationWindow);

    /**
     * Validates that cancellation is allowed, throwing an exception if outside the window.
     */
    default void validateCancellation(ReservationEntity reservation, Instant cancellationTime, Duration cancellationWindow) {
        if (!isCancellationAllowed(reservation, cancellationTime, cancellationWindow)) {
            throw new CancellationWindowExpiredException(reservation.getStartTime(), cancellationTime, cancellationWindow);
        }
    }
}
