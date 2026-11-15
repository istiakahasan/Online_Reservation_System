package com.example.reservation.reservation.domain.event;

import com.example.reservation.user.domain.UserRole;
import java.time.Instant;
import java.util.UUID;

public record ReservationCancelled(
        UUID reservationId,
        UUID businessId,
        Instant occurredAt,
        UUID actorId,
        UserRole actorRole,
        String reason
) implements ReservationDomainEvent {}
