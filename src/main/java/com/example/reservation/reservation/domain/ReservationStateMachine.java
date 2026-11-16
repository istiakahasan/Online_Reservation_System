package com.example.reservation.reservation.domain;

import com.example.reservation.reservation.domain.event.*;
import com.example.reservation.reservation.domain.exception.InvalidReservationTransitionException;
import com.example.reservation.reservation.domain.exception.UnauthorizedReservationActionException;
import com.example.reservation.user.domain.UserRole;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ReservationStateMachine {

    private static final Map<ReservationStatus, Set<UserRole>> ROLE_PERMISSIONS = Map.of(
            ReservationStatus.CONFIRMED, EnumSet.of(UserRole.STAFF, UserRole.ADMIN),
            ReservationStatus.CANCELLED, EnumSet.of(UserRole.CUSTOMER, UserRole.STAFF, UserRole.ADMIN),
            ReservationStatus.COMPLETED, EnumSet.of(UserRole.STAFF, UserRole.ADMIN),
            ReservationStatus.NO_SHOW, EnumSet.of(UserRole.STAFF, UserRole.ADMIN)
    );

    private final UUID reservationId;
    private final UUID businessId;
    private ReservationStatus currentStatus;

    public ReservationStateMachine(UUID reservationId, UUID businessId, ReservationStatus initialStatus) {
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId must not be null");
        this.businessId = Objects.requireNonNull(businessId, "businessId must not be null");
        this.currentStatus = Objects.requireNonNull(initialStatus, "initialStatus must not be null");
    }

    public static ReservationStateMachine of(ReservationEntity reservation) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        return new ReservationStateMachine(
                reservation.getId(),
                reservation.getBusiness().getId(),
                reservation.getStatus()
        );
    }

    public ReservationConfirmed confirm(UUID actorId, UserRole actorRole) {
        validateAndTransition(ReservationStatus.CONFIRMED, actorRole);
        return new ReservationConfirmed(reservationId, businessId, Instant.now(), actorId, actorRole);
    }

    public ReservationCancelled cancel(UUID actorId, UserRole actorRole, String reason) {
        validateAndTransition(ReservationStatus.CANCELLED, actorRole);
        return new ReservationCancelled(reservationId, businessId, Instant.now(), actorId, actorRole, reason);
    }

    public ReservationCompleted complete(UUID actorId, UserRole actorRole) {
        validateAndTransition(ReservationStatus.COMPLETED, actorRole);
        return new ReservationCompleted(reservationId, businessId, Instant.now(), actorId, actorRole);
    }

    public ReservationNoShow markNoShow(UUID actorId, UserRole actorRole) {
        validateAndTransition(ReservationStatus.NO_SHOW, actorRole);
        return new ReservationNoShow(reservationId, businessId, Instant.now(), actorId, actorRole);
    }

    public ReservationDomainEvent transitionTo(ReservationStatus targetStatus, UUID actorId, UserRole actorRole, String reason) {
        return switch (targetStatus) {
            case CONFIRMED -> confirm(actorId, actorRole);
            case CANCELLED -> cancel(actorId, actorRole, reason);
            case COMPLETED -> complete(actorId, actorRole);
            case NO_SHOW -> markNoShow(actorId, actorRole);
            case PENDING -> throw new InvalidReservationTransitionException(currentStatus, targetStatus);
        };
    }

    public ReservationDomainEvent transitionTo(ReservationStatus targetStatus, UUID actorId, UserRole actorRole) {
        return transitionTo(targetStatus, actorId, actorRole, null);
    }

    private void validateAndTransition(ReservationStatus targetStatus, UserRole actorRole) {
        Objects.requireNonNull(targetStatus, "targetStatus must not be null");
        Objects.requireNonNull(actorRole, "actorRole must not be null");

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new InvalidReservationTransitionException(currentStatus, targetStatus);
        }

        Set<UserRole> allowedRoles = ROLE_PERMISSIONS.getOrDefault(targetStatus, Collections.emptySet());
        if (!allowedRoles.contains(actorRole)) {
            throw new UnauthorizedReservationActionException(actorRole, targetStatus);
        }

        this.currentStatus = targetStatus;
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getBusinessId() { return businessId; }
    public ReservationStatus getCurrentStatus() { return currentStatus; }
}
