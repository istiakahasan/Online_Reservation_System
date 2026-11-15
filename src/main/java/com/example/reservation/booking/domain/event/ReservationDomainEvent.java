package com.example.reservation.booking.domain.event;

import com.example.reservation.user.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Base sealed interface for all domain events emitted by the reservation state machine.
 */
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
