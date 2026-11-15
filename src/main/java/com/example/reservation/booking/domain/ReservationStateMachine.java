package com.example.reservation.booking.domain;

import com.example.reservation.booking.domain.event.ReservationCancelled;
import com.example.reservation.booking.domain.event.ReservationConfirmed;
import com.example.reservation.booking.domain.event.ReservationDomainEvent;
import com.example.reservation.booking.domain.event.ReservationCompleted;
import com.example.reservation.booking.domain.event.ReservationNoShow;
import com.example.reservation.booking.domain.exception.InvalidReservationTransitionException;
import com.example.reservation.booking.domain.exception.UnauthorizedReservationActionException;
import com.example.reservation.user.domain.UserRole;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Domain object that governs the lifecycle state transitions of a reservation.
 * Enforces business transition rules, role-based authorization, and produces
 * immutable domain events upon state changes.
 */
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

    /**
     * Confirms a PENDING reservation.
     * Allowed roles: STAFF, ADMIN.
     */
    public ReservationConfirmed confirm(UUID actorId, UserRole actorRole) {
        validateAndTransition(ReservationStatus.CONFIRMED, actorRole);
        return new ReservationConfirmed(reservationId, businessId, Instant.now(), actorId, actorRole);
    }

    /**
     * Cancels a PENDING or CONFIRMED reservation.
     * Allowed roles: CUSTOMER, STAFF, ADMIN.
     */
    public ReservationCancelled cancel(UUID actorId, UserRole actorRole, String reason) {
        validateAndTransition(ReservationStatus.CANCELLED, actorRole);
        return new ReservationCancelled(reservationId, businessId, Instant.now(), actorId, actorRole, reason);
    }

    /**
     * Marks a CONFIRMED reservation as COMPLETED.
     * Allowed roles: STAFF, ADMIN.
     */
    public ReservationCompleted complete(UUID actorId, UserRole actorRole) {
        validateAndTransition(ReservationStatus.COMPLETED, actorRole);
        return new ReservationCompleted(reservationId, businessId, Instant.now(), actorId, actorRole);
    }

    /**
     * Marks a CONFIRMED reservation as NO_SHOW.
     * Allowed roles: STAFF, ADMIN.
     */
    public ReservationNoShow markNoShow(UUID actorId, UserRole actorRole) {
        validateAndTransition(ReservationStatus.NO_SHOW, actorRole);
        return new ReservationNoShow(reservationId, businessId, Instant.now(), actorId, actorRole);
    }

    /**
     * Generic transition dispatcher. Validates transition and role, mutates current state,
     * and returns the corresponding domain event.
     */
    public ReservationDomainEvent transitionTo(ReservationStatus targetStatus, UUID actorId, UserRole actorRole, String reason) {
        return switch (targetStatus) {
            case CONFIRMED -> confirm(actorId, actorRole);
            case CANCELLED -> cancel(actorId, actorRole, reason);
            case COMPLETED -> complete(actorId, actorRole);
            case NO_SHOW -> markNoShow(actorId, actorRole);
            case PENDING -> throw new InvalidReservationTransitionException(currentStatus, targetStatus);
        };
    }

    /**
     * Convenience transition method without explicit cancellation reason.
     */
    public ReservationDomainEvent transitionTo(ReservationStatus targetStatus, UUID actorId, UserRole actorRole) {
        return transitionTo(targetStatus, actorId, actorRole, null);
    }

    private void validateAndTransition(ReservationStatus targetStatus, UserRole actorRole) {
        Objects.requireNonNull(targetStatus, "targetStatus must not be null");
        Objects.requireNonNull(actorRole, "actorRole must not be null");

        // 1. Verify state transition validity
        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new InvalidReservationTransitionException(currentStatus, targetStatus);
        }

        // 2. Verify actor role authorization
        Set<UserRole> allowedRoles = ROLE_PERMISSIONS.getOrDefault(targetStatus, Collections.emptySet());
        if (!allowedRoles.contains(actorRole)) {
            throw new UnauthorizedReservationActionException(actorRole, targetStatus);
        }

        // 3. Mutate state
        this.currentStatus = targetStatus;
    }

    public static boolean isValidTransition(ReservationStatus from, ReservationStatus to) {
        return from != null && from.canTransitionTo(to);
    }

    public static boolean isRoleAuthorized(ReservationStatus targetStatus, UserRole role) {
        if (targetStatus == null || role == null) {
            return false;
        }
        return ROLE_PERMISSIONS.getOrDefault(targetStatus, Collections.emptySet()).contains(role);
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public ReservationStatus getCurrentStatus() {
        return currentStatus;
    }
}
