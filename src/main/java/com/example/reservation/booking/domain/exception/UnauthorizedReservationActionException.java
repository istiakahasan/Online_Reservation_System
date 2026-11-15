package com.example.reservation.booking.domain.exception;

import com.example.reservation.booking.domain.ReservationStatus;
import com.example.reservation.common.exception.AppException;
import com.example.reservation.user.domain.UserRole;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an actor does not have the required role to execute a reservation transition.
 */
public class UnauthorizedReservationActionException extends AppException {

    public UnauthorizedReservationActionException(UserRole actorRole, ReservationStatus targetStatus) {
        super(
                "Role '%s' is not authorized to transition reservation to status '%s'".formatted(actorRole, targetStatus),
                "RESERVATION_UNAUTHORIZED_ACTION",
                HttpStatus.FORBIDDEN
        );
    }
}

