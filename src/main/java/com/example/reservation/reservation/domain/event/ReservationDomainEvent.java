package com.example.reservation.reservation.domain.event;

import com.example.reservation.user.domain.UserRole;
import java.time.Instant;
import java.util.UUID;

public sealed interface ReservationDomainEvent permits
        ReservationConfirmed,
        ReservationCancelled,
        ReservationCompleted,
        ReservationNoShow {
    UUID reservationId();
    UUID businessId();
    Instant occurredAt();
    UUID actorId();
    UserRole actorRole();
}
