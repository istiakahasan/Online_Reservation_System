package com.example.reservation.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reservation.reservation.domain.event.*;
import com.example.reservation.reservation.domain.exception.InvalidReservationTransitionException;
import com.example.reservation.reservation.domain.exception.UnauthorizedReservationActionException;
import com.example.reservation.user.domain.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ReservationStateMachineTest {

    private final UUID reservationId = UUID.randomUUID();
    private final UUID businessId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @Nested
    @DisplayName("Valid Transitions & Event Emission")
    class ValidTransitionsTest {

        @ParameterizedTest(name = "{0} -> {1} by {2} should emit {3}")
        @MethodSource("validTransitionsProvider")
        void shouldSucceedOnValidTransition(
                ReservationStatus initialStatus,
                ReservationStatus targetStatus,
                UserRole actorRole,
                Class<? extends ReservationDomainEvent> expectedEventClass
        ) {
            ReservationStateMachine stateMachine = new ReservationStateMachine(
                    reservationId, businessId, initialStatus
            );

            ReservationDomainEvent event = stateMachine.transitionTo(
                    targetStatus, actorId, actorRole, "Test cancellation reason"
            );

            assertThat(stateMachine.getCurrentStatus()).isEqualTo(targetStatus);
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
                    Arguments.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, UserRole.STAFF, ReservationConfirmed.class),
                    Arguments.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, UserRole.ADMIN, ReservationConfirmed.class),
                    Arguments.of(ReservationStatus.PENDING, ReservationStatus.CANCELLED, UserRole.CUSTOMER, ReservationCancelled.class),
                    Arguments.of(ReservationStatus.PENDING, ReservationStatus.CANCELLED, UserRole.STAFF, ReservationCancelled.class),
                    Arguments.of(ReservationStatus.PENDING, ReservationStatus.CANCELLED, UserRole.ADMIN, ReservationCancelled.class),
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

        @ParameterizedTest(name = "{0} -> {1} is invalid")
        @MethodSource("invalidTransitionsProvider")
        void shouldRejectInvalidTransitions(ReservationStatus from, ReservationStatus to) {
            ReservationStateMachine stateMachine = new ReservationStateMachine(
                    reservationId, businessId, from
            );

            assertThatThrownBy(() -> stateMachine.transitionTo(to, actorId, UserRole.ADMIN))
                    .isInstanceOf(InvalidReservationTransitionException.class);

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
        void customerCannotPerformPrivilegedTransitions(ReservationStatus from, ReservationStatus to) {
            ReservationStateMachine stateMachine = new ReservationStateMachine(
                    reservationId, businessId, from
            );

            assertThatThrownBy(() -> stateMachine.transitionTo(to, actorId, UserRole.CUSTOMER))
                    .isInstanceOf(UnauthorizedReservationActionException.class);

            assertThat(stateMachine.getCurrentStatus()).isEqualTo(from);
        }

        @ParameterizedTest
        @EnumSource(value = ReservationStatus.class, names = {"PENDING", "CONFIRMED"})
        void allRolesCanCancel(ReservationStatus from) {
            for (UserRole role : UserRole.values()) {
                ReservationStateMachine stateMachine = new ReservationStateMachine(
                        reservationId, businessId, from
                );

                ReservationCancelled event = stateMachine.cancel(actorId, role, "Reason");

                assertThat(stateMachine.getCurrentStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(event.actorRole()).isEqualTo(role);
            }
        }
    }
}
