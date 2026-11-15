package com.example.reservation.booking.domain.event;

import com.example.reservation.user.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a confirmed reservation is marked as completed by staff or admin.
 */
public record ReservationCompleted(
        UUID reservationId,
        UUID businessId,
        Instant occurredAt,
        UUID actorId,
        UserRole actorRole
) implements ReservationDomainEvent {

    public ReservationCompleted {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
