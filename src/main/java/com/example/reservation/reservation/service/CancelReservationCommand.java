package com.example.reservation.reservation.service;

import com.example.reservation.user.domain.UserRole;
import java.time.Instant;
import java.util.UUID;

public record CancelReservationCommand(
        UUID reservationId,
        UUID actorId,
        UserRole actorRole,
        String reason,
        Instant cancellationTime
) {
    public CancelReservationCommand(UUID reservationId, UUID actorId, UserRole actorRole, String reason) {
        this(reservationId, actorId, actorRole, reason, Instant.now());
    }
}
