package com.example.reservation.reservation.service;

import com.example.reservation.business.domain.BusinessEntity;
import com.example.reservation.payment.domain.PaymentEntity;
import com.example.reservation.payment.repo.PaymentRepository;
import com.example.reservation.payment.service.StripeRefundResult;
import com.example.reservation.payment.service.StripeRefundService;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.domain.event.ReservationCancelled;
import com.example.reservation.reservation.domain.exception.CancellationWindowExpiredException;
import com.example.reservation.reservation.domain.exception.InvalidReservationTransitionException;
import com.example.reservation.reservation.domain.policy.CancellationPolicy;
import com.example.reservation.reservation.domain.policy.FullRefundPolicy;
import com.example.reservation.reservation.domain.policy.NoRefundPolicy;
import com.example.reservation.reservation.domain.policy.PartialRefundPolicy;
import com.example.reservation.reservation.domain.policy.RefundPolicyRegistry;
import com.example.reservation.reservation.domain.policy.RefundPolicyType;
import com.example.reservation.reservation.domain.policy.WindowBasedCancellationPolicy;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.service.domain.ServiceEntity;
import com.example.reservation.user.domain.UserEntity;
import com.example.reservation.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelReservationUseCaseTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StripeRefundService stripeRefundService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CancellationPolicy cancellationPolicy;
    private RefundPolicyRegistry refundPolicyRegistry;
    private CancelReservationUseCase cancelReservationUseCase;

    private BusinessEntity business;
    private UserEntity customer;
    private UserEntity staff;
    private ServiceEntity service;

    private UUID reservationId;
    private UUID customerId;
    private Instant reservationStartTime;

    @BeforeEach
    void setUp() {
        cancellationPolicy = new WindowBasedCancellationPolicy();
        refundPolicyRegistry = new RefundPolicyRegistry(List.of(
                new FullRefundPolicy(),
                new PartialRefundPolicy(new BigDecimal("50.00")),
                new NoRefundPolicy()
        ));

        cancelReservationUseCase = new CancelReservationUseCase(
                reservationRepository,
                paymentRepository,
                cancellationPolicy,
                refundPolicyRegistry,
                stripeRefundService,
                eventPublisher
        );

        reservationId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        // Business configured with 24h cancellation window and FULL refund policy
        business = new BusinessEntity("Spa Oasis", "spa-oasis", "UTC", "contact@spa.com", "+1-555-0100", 24, RefundPolicyType.FULL);
        business.setId(UUID.randomUUID());

        customer = new UserEntity(business, "Jane", "Doe", "jane@example.com", "hash", "+1-555-0101", UserRole.CUSTOMER);
        customer.setId(customerId);

        staff = new UserEntity(business, "Dr. Alice", "Smith", "alice@example.com", "hash", "+1-555-0102", UserRole.STAFF);
        staff.setId(UUID.randomUUID());

        service = new ServiceEntity(business, "Swedish Massage", "60 min", 60, 0, new BigDecimal("100.00"), "USD");
        service.setId(UUID.randomUUID());

        // Reservation starts 3 days from now
        reservationStartTime = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    }

    private ReservationEntity createConfirmedReservation() {
        ReservationEntity reservation = new ReservationEntity(
                business, customer, service, staff,
                reservationStartTime, reservationStartTime.plus(60, ChronoUnit.MINUTES),
                new BigDecimal("100.00"), "USD", null, "Special request"
        );
        reservation.setId(reservationId);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return reservation;
    }

    @Test
    @DisplayName("Within window: Successfully cancels CONFIRMED reservation, triggers Stripe full refund, and publishes event")
    void cancelReservation_WithinWindow_TriggersFullRefund_AndPublishesEvent() {
        ReservationEntity reservation = createConfirmedReservation();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationRepository.saveAndFlush(any(ReservationEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Payment exists: $100.00 SUCCEEDED via Stripe pi_12345
        PaymentEntity payment = new PaymentEntity(business, reservation, "pi_12345", new BigDecimal("100.00"), "USD", "SUCCEEDED");
        payment.setId(UUID.randomUUID());
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        when(stripeRefundService.processRefund(eq("pi_12345"), eq(new BigDecimal("100.00")), eq("USD"), any()))
                .thenReturn(StripeRefundResult.success("re_test_9999", new BigDecimal("100.00"), "USD", "succeeded"));

        // Cancellation requested 48 hours before start (window is 24 hours -> within window)
        Instant cancellationTime = reservationStartTime.minus(48, ChronoUnit.HOURS);
        CancelReservationCommand command = new CancelReservationCommand(
                reservationId, customerId, UserRole.CUSTOMER, "Travel plans changed", cancellationTime
        );

        CancelReservationResult result = cancelReservationUseCase.execute(command);

        assertThat(result.isCancelled()).isTrue();
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.wasPaid()).isTrue();
        assertThat(result.refundTriggered()).isTrue();
        assertThat(result.refundAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(result.refundId()).isEqualTo("re_test_9999");
        assertThat(result.refundStatus()).isEqualTo("succeeded");

        // Assert reservation status transitioned to CANCELLED
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);

        // Assert payment record updated with refund ID
        assertThat(payment.getStripeRefundId()).isEqualTo("re_test_9999");
        assertThat(payment.getStatus()).isEqualTo("REFUNDED");

        // Assert ReservationCancelled event published
        ArgumentCaptor<ReservationCancelled> eventCaptor = ArgumentCaptor.forClass(ReservationCancelled.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ReservationCancelled event = eventCaptor.getValue();
        assertThat(event.reservationId()).isEqualTo(reservationId);
        assertThat(event.actorId()).isEqualTo(customerId);
        assertThat(event.reason()).isEqualTo("Travel plans changed");
    }

    @Test
    @DisplayName("Outside window: Throws CancellationWindowExpiredException when cancelling too late (< 24h before start)")
    void cancelReservation_OutsideWindow_ThrowsCancellationWindowExpiredException() {
        ReservationEntity reservation = createConfirmedReservation();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        // Attempting to cancel 2 hours before start time (business requires >= 24h notice)
        Instant cancellationTime = reservationStartTime.minus(2, ChronoUnit.HOURS);
        CancelReservationCommand command = new CancelReservationCommand(
                reservationId, customerId, UserRole.CUSTOMER, "Emergency cancellation", cancellationTime
        );

        assertThatThrownBy(() -> cancelReservationUseCase.execute(command))
                .isInstanceOf(CancellationWindowExpiredException.class)
                .hasMessageContaining("Cancellation window of 24 hour(s) before start time");

        // Verify reservation status was NOT changed
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        // Verify Stripe was NEVER called
        verify(stripeRefundService, never()).processRefund(any(), any(), any(), any());

        // Verify event was NEVER published
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Unpaid reservation: Successfully cancels within window, no Stripe refund triggered")
    void cancelReservation_UnpaidReservation_CancelsWithoutRefund() {
        ReservationEntity reservation = createConfirmedReservation();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationRepository.saveAndFlush(any(ReservationEntity.class))).thenAnswer(i -> i.getArgument(0));

        // No payment record exists in paymentRepository
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());

        Instant cancellationTime = reservationStartTime.minus(48, ChronoUnit.HOURS);
        CancelReservationCommand command = new CancelReservationCommand(
                reservationId, customerId, UserRole.CUSTOMER, "No longer needed", cancellationTime
        );

        CancelReservationResult result = cancelReservationUseCase.execute(command);

        assertThat(result.isCancelled()).isTrue();
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.wasPaid()).isFalse();
        assertThat(result.refundTriggered()).isFalse();
        assertThat(result.refundAmount()).isEqualTo(BigDecimal.ZERO);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(stripeRefundService, never()).processRefund(any(), any(), any(), any());
        verify(eventPublisher).publishEvent(any(ReservationCancelled.class));
    }

    @Test
    @DisplayName("Already cancelled: Throws InvalidReservationTransitionException via state machine")
    void cancelReservation_AlreadyCancelled_ThrowsInvalidReservationTransitionException() {
        ReservationEntity reservation = createConfirmedReservation();
        reservation.setStatus(ReservationStatus.CANCELLED); // Already cancelled!

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        Instant cancellationTime = reservationStartTime.minus(48, ChronoUnit.HOURS);
        CancelReservationCommand command = new CancelReservationCommand(
                reservationId, customerId, UserRole.CUSTOMER, "Cancelling again", cancellationTime
        );

        assertThatThrownBy(() -> cancelReservationUseCase.execute(command))
                .isInstanceOf(InvalidReservationTransitionException.class)
                .hasMessageContaining("Invalid reservation status transition from 'CANCELLED' to 'CANCELLED'");

        verify(stripeRefundService, never()).processRefund(any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Partial refund policy: Grants 50% refund when business refund policy is PARTIAL")
    void cancelReservation_PartialRefundPolicy_CalculatesPartialAmount() {
        // Set business policy to PARTIAL
        business.setRefundPolicyType(RefundPolicyType.PARTIAL);

        ReservationEntity reservation = createConfirmedReservation();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationRepository.saveAndFlush(any(ReservationEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Payment of $100.00
        PaymentEntity payment = new PaymentEntity(business, reservation, "pi_partial_123", new BigDecimal("100.00"), "USD", "SUCCEEDED");
        payment.setId(UUID.randomUUID());
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        when(stripeRefundService.processRefund(eq("pi_partial_123"), eq(new BigDecimal("50.00")), eq("USD"), any()))
                .thenReturn(StripeRefundResult.success("re_partial_456", new BigDecimal("50.00"), "USD", "succeeded"));

        Instant cancellationTime = reservationStartTime.minus(48, ChronoUnit.HOURS);
        CancelReservationCommand command = new CancelReservationCommand(
                reservationId, customerId, UserRole.CUSTOMER, "Partial refund requested", cancellationTime
        );

        CancelReservationResult result = cancelReservationUseCase.execute(command);

        assertThat(result.isCancelled()).isTrue();
        assertThat(result.refundTriggered()).isTrue();
        assertThat(result.refundAmount()).isEqualTo(new BigDecimal("50.00")); // 50% of 100.00
        assertThat(result.refundId()).isEqualTo("re_partial_456");

        verify(stripeRefundService).processRefund(eq("pi_partial_123"), eq(new BigDecimal("50.00")), eq("USD"), any());
    }

    @Test
    @DisplayName("No refund policy: Cancels reservation without issuing refund when business policy is NONE")
    void cancelReservation_NoRefundPolicy_NoRefundTriggered() {
        business.setRefundPolicyType(RefundPolicyType.NONE);

        ReservationEntity reservation = createConfirmedReservation();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationRepository.saveAndFlush(any(ReservationEntity.class))).thenAnswer(i -> i.getArgument(0));

        PaymentEntity payment = new PaymentEntity(business, reservation, "pi_none_123", new BigDecimal("100.00"), "USD", "SUCCEEDED");
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.of(payment));

        Instant cancellationTime = reservationStartTime.minus(48, ChronoUnit.HOURS);
        CancelReservationCommand command = new CancelReservationCommand(
                reservationId, customerId, UserRole.CUSTOMER, "Non-refundable cancel", cancellationTime
        );

        CancelReservationResult result = cancelReservationUseCase.execute(command);

        assertThat(result.isCancelled()).isTrue();
        assertThat(result.refundTriggered()).isFalse();
        assertThat(result.refundAmount()).isEqualTo(BigDecimal.ZERO);
        verify(stripeRefundService, never()).processRefund(any(), any(), any(), any());
    }
}
