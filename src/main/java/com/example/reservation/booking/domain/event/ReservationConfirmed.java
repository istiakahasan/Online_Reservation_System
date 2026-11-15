package com.example.reservation.booking.domain.event;

import com.example.reservation.user.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a reservation transitions from PENDING to CONFIRMED.
 */
public record ReservationConfirmed(
        UUID reservationId,
        UUID businessId,
        Instant occurredAt,
        UUID actorId,
        UserRole actorRole
) implements ReservationDomainEvent {

    public ReservationConfirmed {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}

