package com.example.reservation.reservation.domain.exception;

import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.shared.exception.AppException;
import org.springframework.http.HttpStatus;

public class InvalidReservationTransitionException extends AppException {
    public InvalidReservationTransitionException(ReservationStatus from, ReservationStatus to) {
        super(
                "Invalid reservation status transition from '%s' to '%s'".formatted(from, to),
                "RESERVATION_INVALID_TRANSITION",
                HttpStatus.CONFLICT
        );
    }
}
