package com.example.reservation.booking.domain.event;

import com.example.reservation.user.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a customer fails to show up for their confirmed reservation.
 */
public record ReservationNoShow(
        UUID reservationId,
        UUID businessId,
        Instant occurredAt,
        UUID actorId,
        UserRole actorRole
) implements ReservationDomainEvent {

    public ReservationNoShow {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
