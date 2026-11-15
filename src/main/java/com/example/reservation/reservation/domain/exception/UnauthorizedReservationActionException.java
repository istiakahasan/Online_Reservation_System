package com.example.reservation.reservation.domain.exception;

import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.shared.exception.AppException;
import com.example.reservation.user.domain.UserRole;
import org.springframework.http.HttpStatus;

public class UnauthorizedReservationActionException extends AppException {
    public UnauthorizedReservationActionException(UserRole actorRole, ReservationStatus targetStatus) {
        super(
                "Role '%s' is not authorized to transition reservation to status '%s'".formatted(actorRole, targetStatus),
                "RESERVATION_UNAUTHORIZED_ACTION",
                HttpStatus.FORBIDDEN
        );
    }
}
