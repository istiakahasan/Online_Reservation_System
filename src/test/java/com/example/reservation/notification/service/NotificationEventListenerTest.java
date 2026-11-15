package com.example.reservation.notification.service;

import com.example.reservation.business.domain.BusinessEntity;
import com.example.reservation.notification.service.channel.EmailChannel;
import com.example.reservation.notification.service.channel.NotificationRequest;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.domain.event.ReservationCancelled;
import com.example.reservation.reservation.domain.event.ReservationConfirmed;
import com.example.reservation.reservation.domain.event.ReservationCreatedEvent;
import com.example.reservation.reservation.domain.event.ReservationReminderDue;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.service.domain.ServiceEntity;
import com.example.reservation.user.domain.UserEntity;
import com.example.reservation.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private EmailChannel emailChannel;

    @InjectMocks
    private NotificationEventListener listener;

    private BusinessEntity business;
    private UserEntity customer;
    private UserEntity staff;
    private ServiceEntity service;
    private ReservationEntity reservation;

    private UUID reservationId;
    private UUID businessId;
    private UUID customerId;
    private UUID staffId;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        listener.reset();

        businessId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        reservationId = UUID.randomUUID();

        business = new BusinessEntity("Luxe Wellness", "luxe-wellness", "UTC", "info@luxe.com", "+1-555-0199");
        business.setId(businessId);

        customer = new UserEntity(business, "Emma", "Watson", "emma.watson@example.com", "hash", "+1-555-0191", UserRole.CUSTOMER);
        customer.setId(customerId);

        staff = new UserEntity(business, "Dr. James", "Wilson", "james.wilson@example.com", "hash", "+1-555-0192", UserRole.STAFF);
        staff.setId(staffId);

        service = new ServiceEntity(business, "Aromatherapy Session", "Relaxation therapy", 60, 15, new BigDecimal("110.00"), "USD");
        service.setId(serviceId);

        reservation = new ReservationEntity(
                business, customer, service, staff,
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS).plus(60, ChronoUnit.MINUTES),
                new BigDecimal("110.00"), "USD",
                Instant.now().plus(15, ChronoUnit.MINUTES),
                "Customer preference: lavender oil"
        );
        reservation.setId(reservationId);
    }

    @Test
    @DisplayName("ReservationCreated -> handler is called exactly once, sends 'pending' email")
    void onReservationCreated_DispatchesPendingEmail_ExactlyOnce() {
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        ReservationCreatedEvent event = new ReservationCreatedEvent(
                reservationId, businessId, customerId, serviceId, staffId,
                reservation.getStartTime(), reservation.getEndTime(),
                reservation.getTotalPrice(), reservation.getCurrency(),
                reservation.getHoldExpiresAt(), Instant.now()
        );

        listener.onReservationCreated(event);

        assertThat(listener.getCreatedHandledCount()).isEqualTo(1);

        ArgumentCaptor<NotificationRequest> requestCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(emailChannel, times(1)).send(requestCaptor.capture());

        NotificationRequest request = requestCaptor.getValue();
        assertThat(request.reservationId()).isEqualTo(reservationId);
        assertThat(request.recipient()).isEqualTo("emma.watson@example.com");
        assertThat(request.templateName()).isEqualTo("email/reservation-pending");
        assertThat(request.templateModel()).containsEntry("serviceName", "Aromatherapy Session");
        assertThat(request.templateModel()).containsEntry("totalPrice", new BigDecimal("110.00"));
    }

    @Test
    @DisplayName("ReservationConfirmed -> handler is called exactly once, sends 'confirmed' email")
    void onReservationConfirmed_DispatchesConfirmationEmail_ExactlyOnce() {
        reservation.setStatus(ReservationStatus.CONFIRMED);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        ReservationConfirmed event = new ReservationConfirmed(
                reservationId, businessId, Instant.now(), customerId, UserRole.CUSTOMER
        );

        listener.onReservationConfirmed(event);

        assertThat(listener.getConfirmedHandledCount()).isEqualTo(1);

        ArgumentCaptor<NotificationRequest> requestCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(emailChannel, times(1)).send(requestCaptor.capture());

        NotificationRequest request = requestCaptor.getValue();
        assertThat(request.reservationId()).isEqualTo(reservationId);
        assertThat(request.recipient()).isEqualTo("emma.watson@example.com");
        assertThat(request.templateName()).isEqualTo("email/reservation-confirmed");
        assertThat(request.templateModel()).containsEntry("staffName", "Dr. James Wilson");
        assertThat(request.templateModel()).containsEntry("businessName", "Luxe Wellness");
    }

    @Test
    @DisplayName("ReservationCancelled -> handler is called exactly once, sends 'cancelled' email")
    void onReservationCancelled_DispatchesCancellationEmail_ExactlyOnce() {
        reservation.setStatus(ReservationStatus.CANCELLED);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        ReservationCancelled event = new ReservationCancelled(
                reservationId, businessId, Instant.now(), customerId, UserRole.CUSTOMER, "Schedule conflict"
        );

        listener.onReservationCancelled(event);

        assertThat(listener.getCancelledHandledCount()).isEqualTo(1);

        ArgumentCaptor<NotificationRequest> requestCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(emailChannel, times(1)).send(requestCaptor.capture());

        NotificationRequest request = requestCaptor.getValue();
        assertThat(request.reservationId()).isEqualTo(reservationId);
        assertThat(request.recipient()).isEqualTo("emma.watson@example.com");
        assertThat(request.templateName()).isEqualTo("email/reservation-cancelled");
        assertThat(request.templateModel()).containsEntry("reason", "Schedule conflict");
    }

    @Test
    @DisplayName("ReservationReminderDue -> handler is called exactly once, sends 'reminder' email")
    void onReservationReminderDue_DispatchesReminderEmail_ExactlyOnce() {
        reservation.setStatus(ReservationStatus.CONFIRMED);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        ReservationReminderDue event = new ReservationReminderDue(
                reservationId, businessId, customerId, staffId, reservation.getStartTime(), Instant.now()
        );

        listener.onReservationReminderDue(event);

        assertThat(listener.getReminderHandledCount()).isEqualTo(1);

        ArgumentCaptor<NotificationRequest> requestCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(emailChannel, times(1)).send(requestCaptor.capture());

        NotificationRequest request = requestCaptor.getValue();
        assertThat(request.reservationId()).isEqualTo(reservationId);
        assertThat(request.recipient()).isEqualTo("emma.watson@example.com");
        assertThat(request.templateName()).isEqualTo("email/reservation-reminder");
        assertThat(request.templateModel()).containsEntry("serviceName", "Aromatherapy Session");
    }

    @Test
    @DisplayName("Email channel failure does not throw exception or roll back caller")
    void onReservationConfirmed_EmailChannelThrows_DoesNotPropagate() {
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        doThrow(new RuntimeException("SMTP connection timed out")).when(emailChannel).send(any());

        ReservationConfirmed event = new ReservationConfirmed(
                reservationId, businessId, Instant.now(), customerId, UserRole.CUSTOMER
        );

        // Must not throw exception
        listener.onReservationConfirmed(event);

        assertThat(listener.getConfirmedHandledCount()).isEqualTo(1);
        verify(emailChannel, times(1)).send(any());
    }
}
