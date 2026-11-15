package com.example.reservation.booking.domain.event;

import com.example.reservation.user.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a reservation is cancelled (from PENDING or CONFIRMED).
 */
public record ReservationCancelled(
        UUID reservationId,
        UUID businessId,
        Instant occurredAt,
        UUID actorId,
        UserRole actorRole,
        String reason
) implements ReservationDomainEvent {

    public ReservationCancelled {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
