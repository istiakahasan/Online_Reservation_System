package com.example.reservation.reservation.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.reservation.business.domain.BusinessEntity;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.domain.event.ReservationCancelled;
import com.example.reservation.reservation.domain.event.ReservationNoShow;
import com.example.reservation.reservation.domain.event.ReservationReminderDue;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.service.domain.ServiceEntity;
import com.example.reservation.user.domain.UserEntity;
import com.example.reservation.user.domain.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ReservationScheduledJobsTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReservationScheduledJobs scheduledJobs;

    private BusinessEntity business;
    private UserEntity customer;
    private UserEntity staff;
    private ServiceEntity service;

    @BeforeEach
    void setUp() {
        business = new BusinessEntity("Dental Care", "dental-care", "UTC", "contact@dental.com", "+1-555-0100");
        business.setId(UUID.randomUUID());

        customer = new UserEntity(business, "Alice", "Brown", "alice@example.com", "hash", "+1-555-0101", UserRole.CUSTOMER);
        customer.setId(UUID.randomUUID());

        staff = new UserEntity(business, "Dr. Smith", "Dentist", "smith@example.com", "hash", "+1-555-0102", UserRole.STAFF);
        staff.setId(UUID.randomUUID());

        service = new ServiceEntity(business, "Teeth Cleaning", "Cleaning session", 45, 15, new BigDecimal("80.00"), "USD");
        service.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("expirePendingReservations: Cancels PENDING reservations older than 15 min and publishes ReservationCancelled")
    void expirePendingReservations_Success() {
        Instant now = Instant.now();
        Instant startTime = now.plus(2, ChronoUnit.DAYS);

        ReservationEntity pending1 = new ReservationEntity(
                business, customer, service, staff, startTime, startTime.plus(45, ChronoUnit.MINUTES),
                new BigDecimal("80.00"), "USD", now.minus(5, ChronoUnit.MINUTES), null
        );
        pending1.setId(UUID.randomUUID());
        pending1.setStatus(ReservationStatus.PENDING);

        ReservationEntity pending2 = new ReservationEntity(
                business, customer, service, staff, startTime, startTime.plus(45, ChronoUnit.MINUTES),
                new BigDecimal("80.00"), "USD", now.minus(10, ChronoUnit.MINUTES), null
        );
        pending2.setId(UUID.randomUUID());
        pending2.setStatus(ReservationStatus.PENDING);

        when(reservationRepository.findPendingForExpiration(eq(ReservationStatus.PENDING), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(pending1, pending2));

        int processed = scheduledJobs.expirePendingReservations();

        assertThat(processed).isEqualTo(2);
        assertThat(pending1.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(pending2.getStatus()).isEqualTo(ReservationStatus.CANCELLED);

        verify(reservationRepository, times(2)).save(any(ReservationEntity.class));

        ArgumentCaptor<ReservationCancelled> eventCaptor = ArgumentCaptor.forClass(ReservationCancelled.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

        List<ReservationCancelled> events = eventCaptor.getAllValues();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).reason()).contains("hold expired automatically");
    }

    @Test
    @DisplayName("sendReminders: Dispatches reminders for CONFIRMED bookings starting in ~24h and marks reminderSentAt")
    void sendReminders_Success_AndIdempotent() {
        Instant now = Instant.now();
        Instant startTime = now.plus(24, ChronoUnit.HOURS);

        ReservationEntity confirmed = new ReservationEntity(
                business, customer, service, staff, startTime, startTime.plus(45, ChronoUnit.MINUTES),
                new BigDecimal("80.00"), "USD", null, null
        );
        confirmed.setId(UUID.randomUUID());
        confirmed.setStatus(ReservationStatus.CONFIRMED);
        confirmed.setReminderSentAt(null);

        when(reservationRepository.findConfirmedForReminder(eq(ReservationStatus.CONFIRMED), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(confirmed));

        int processed = scheduledJobs.sendReminders();

        assertThat(processed).isEqualTo(1);
        assertThat(confirmed.getReminderSentAt()).isNotNull();

        verify(reservationRepository).save(confirmed);

        ArgumentCaptor<ReservationReminderDue> eventCaptor = ArgumentCaptor.forClass(ReservationReminderDue.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        ReservationReminderDue reminderEvent = eventCaptor.getValue();
        assertThat(reminderEvent.reservationId()).isEqualTo(confirmed.getId());
        assertThat(reminderEvent.customerId()).isEqualTo(customer.getId());
        assertThat(reminderEvent.startTime()).isEqualTo(startTime);
    }

    @Test
    @DisplayName("markNoShows: Transitions CONFIRMED reservation to NO_SHOW when startTime + 1h has elapsed")
    void markNoShows_Success() {
        Instant now = Instant.now();
        // Reservation started 2 hours ago (startTime + 1 hour has passed)
        Instant startTime = now.minus(2, ChronoUnit.HOURS);

        ReservationEntity confirmed = new ReservationEntity(
                business, customer, service, staff, startTime, startTime.plus(45, ChronoUnit.MINUTES),
                new BigDecimal("80.00"), "USD", null, null
        );
        confirmed.setId(UUID.randomUUID());
        confirmed.setStatus(ReservationStatus.CONFIRMED);

        when(reservationRepository.findConfirmedPastStartTime(eq(ReservationStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(List.of(confirmed));

        int processed = scheduledJobs.markNoShows();

        assertThat(processed).isEqualTo(1);
        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);

        verify(reservationRepository).save(confirmed);

        ArgumentCaptor<ReservationNoShow> eventCaptor = ArgumentCaptor.forClass(ReservationNoShow.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        ReservationNoShow noShowEvent = eventCaptor.getValue();
        assertThat(noShowEvent.reservationId()).isEqualTo(confirmed.getId());
    }
}
