package com.example.reservation.booking.domain;

import com.example.reservation.booking.domain.event.ReservationCancelled;
import com.example.reservation.booking.domain.event.ReservationCompleted;
import com.example.reservation.booking.domain.event.ReservationConfirmed;
import com.example.reservation.booking.domain.event.ReservationDomainEvent;
import com.example.reservation.booking.domain.event.ReservationNoShow;
import com.example.reservation.booking.domain.exception.InvalidReservationTransitionException;
import com.example.reservation.booking.domain.exception.UnauthorizedReservationActionException;
import com.example.reservation.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationStateMachineTest {

    private final UUID reservationId = UUID.randomUUID();
    private final UUID businessId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @Nested
    @DisplayName("Valid Transitions & Event Emission")
    class ValidTransitionsTest {

        @ParameterizedTest(name = "{0} -> {1} by {2} should emit {3}")
        @MethodSource("validTransitionsProvider")
        @DisplayName("Every valid transition succeeds, updates state, and returns expected event")
        void shouldSucceedOnValidTransition(
                ReservationStatus initialStatus,
                ReservationStatus targetStatus,
                UserRole actorRole,
                Class<? extends ReservationDomainEvent> expectedEventClass
        ) {
            // Arrange
            ReservationStateMachine stateMachine = new ReservationStateMachine(
                    reservationId,
                    businessId,
                    initialStatus
            );

            // Act
            ReservationDomainEvent event = stateMachine.transitionTo(
                    targetStatus,
                    actorId,
                    actorRole,
                    "Test cancellation reason"
            );

            // Assert
            assertThat(stateMachine.getCurrentStatus()).isEqualTo(targetStatus);
            assertThat(event).isNotNull();
            assertThat(event).isInstanceOf(expectedEventClass);
            assertThat(event.reservationId()).isEqualTo(reservationId);
            assertThat(event.businessId()).isEqualTo(businessId);
            assertThat(event.actorId()).isEqualTo(actorId);
            assertThat(event.actorRole()).isEqualTo(actorRole);
            assertThat(event.occurredAt()).isNotNull();

            if (event instanceof ReservationCancelled cancelledEvent) {
                assertThat(cancelledEvent.reason()).isEqualTo("Test cancellation reason");
            }
        }

        static Stream<Arguments> validTransitionsProvider() {
            return Stream.of(
                    // PENDING transitions
                    Arguments.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, UserRole.STAFF, ReservationConfirmed.class),
                    Arguments.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, UserRole.ADMIN, ReservationConfirmed.class),
                    Arguments.of(ReservationStatus.PENDING, ReservationStatus.CANCELLED, UserRole.CUSTOMER, ReservationCancelled.class),
                    Arguments.of(ReservationStatus.PENDING, ReservationStatus.CANCELLED, UserRole.STAFF, ReservationCancelled.class),
                    Arguments.of(ReservationStatus.PENDING, ReservationStatus.CANCELLED, UserRole.ADMIN, ReservationCancelled.class),

                    // CONFIRMED transitions
                    Arguments.of(ReservationStatus.CONFIRMED, ReservationStatus.COMPLETED, UserRole.STAFF, ReservationCompleted.class),
                    Arguments.of(ReservationStatus.CONFIRMED, ReservationStatus.COMPLETED, UserRole.ADMIN, ReservationCompleted.class),
                    Arguments.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, UserRole.CUSTOMER, ReservationCancelled.class),
                    Arguments.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, UserRole.STAFF, ReservationCancelled.class),
                    Arguments.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, UserRole.ADMIN, ReservationCancelled.class),
                    Arguments.of(ReservationStatus.CONFIRMED, ReservationStatus.NO_SHOW, UserRole.STAFF, ReservationNoShow.class),
                    Arguments.of(ReservationStatus.CONFIRMED, ReservationStatus.NO_SHOW, UserRole.ADMIN, ReservationNoShow.class)
            );
        }
    }

    @Nested
    @DisplayName("Invalid State Transitions")
    class InvalidTransitionsTest {

        @ParameterizedTest(name = "{0} -> {1} is invalid and must throw InvalidReservationTransitionException")
        @MethodSource("invalidTransitionsProvider")
        @DisplayName("All 20 disallowed transitions in the 5x5 matrix are rejected")
        void shouldRejectInvalidTransitions(ReservationStatus from, ReservationStatus to) {
            ReservationStateMachine stateMachine = new ReservationStateMachine(
                    reservationId,
                    businessId,
                    from
            );

            assertThatThrownBy(() -> stateMachine.transitionTo(to, actorId, UserRole.ADMIN))
                    .isInstanceOf(InvalidReservationTransitionException.class)
                    .hasMessageContaining("Invalid reservation status transition from '%s' to '%s'".formatted(from, to));

            // Verify state was not mutated
            assertThat(stateMachine.getCurrentStatus()).isEqualTo(from);
        }

        static Stream<Arguments> invalidTransitionsProvider() {
            List<Arguments> invalidPairs = new ArrayList<>();
            for (ReservationStatus from : ReservationStatus.values()) {
                for (ReservationStatus to : ReservationStatus.values()) {
                    if (!from.canTransitionTo(to)) {
                        invalidPairs.add(Arguments.of(from, to));
                    }
                }
            }
            return invalidPairs.stream();
        }
    }

    @Nested
    @DisplayName("Role-Based Authorization Rules")
    class RoleAuthorizationTest {

        @ParameterizedTest
        @CsvSource({
                "PENDING, CONFIRMED",
                "CONFIRMED, COMPLETED",
                "CONFIRMED, NO_SHOW"
        })
        @DisplayName("CUSTOMER role is forbidden from confirming, completing, or marking no-show")
        void customerCannotPerformPrivilegedTransitions(ReservationStatus from, ReservationStatus to) {
            ReservationStateMachine stateMachine = new ReservationStateMachine(
                    reservationId,
                    businessId,
                    from
            );

            assertThatThrownBy(() -> stateMachine.transitionTo(to, actorId, UserRole.CUSTOMER))
                    .isInstanceOf(UnauthorizedReservationActionException.class)
                    .hasMessageContaining("Role 'CUSTOMER' is not authorized to transition reservation to status '%s'".formatted(to));

            assertThat(stateMachine.getCurrentStatus()).isEqualTo(from);
        }

        @ParameterizedTest
        @EnumSource(value = ReservationStatus.class, names = {"PENDING", "CONFIRMED"})
        @DisplayName("CUSTOMER, STAFF, and ADMIN are all authorized to cancel reservations")
        void allRolesCanCancel(ReservationStatus from) {
            for (UserRole role : UserRole.values()) {
                ReservationStateMachine stateMachine = new ReservationStateMachine(
                        reservationId,
                        businessId,
                        from
                );

                ReservationCancelled event = stateMachine.cancel(actorId, role, "Customer requested refund");

                assertThat(stateMachine.getCurrentStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(event.actorRole()).isEqualTo(role);
                assertThat(event.reason()).isEqualTo("Customer requested refund");
            }
        }
    }
}

