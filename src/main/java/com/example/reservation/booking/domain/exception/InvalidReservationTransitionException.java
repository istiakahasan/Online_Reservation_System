package com.example.reservation.booking.domain.exception;

import com.example.reservation.booking.domain.ReservationStatus;
import com.example.reservation.common.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an illegal state transition is attempted on a reservation.
 */
public class InvalidReservationTransitionException extends AppException {

    public InvalidReservationTransitionException(ReservationStatus from, ReservationStatus to) {
        super(
                "Invalid reservation status transition from '%s' to '%s'".formatted(from, to),
                "RESERVATION_INVALID_TRANSITION",
                HttpStatus.CONFLICT
        );
    }
}
