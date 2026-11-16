package com.example.reservation.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.reservation.availability.domain.AvailableSlot;
import com.example.reservation.availability.service.AvailabilityService;
import com.example.reservation.business.domain.BusinessEntity;
import com.example.reservation.business.repo.BusinessRepository;
import com.example.reservation.reservation.api.dto.CreateReservationRequestDto;
import com.example.reservation.reservation.api.dto.ReservationResponseDto;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.domain.event.ReservationCreatedEvent;
import com.example.reservation.reservation.domain.exception.SlotAlreadyBookedException;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.service.domain.ServiceEntity;
import com.example.reservation.service.repo.ServiceRepository;
import com.example.reservation.shared.exception.AppException;
import com.example.reservation.shared.observability.ReservationMetrics;
import com.example.reservation.user.domain.UserEntity;
import com.example.reservation.user.domain.UserRole;
import com.example.reservation.user.repo.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private CancelReservationUseCase cancelReservationUseCase;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ReservationMetrics reservationMetrics;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    private BusinessEntity business;
    private UserEntity customer;
    private UserEntity staff;
    private ServiceEntity service;

    private UUID businessId;
    private UUID customerId;
    private UUID staffId;
    private UUID serviceId;

    private Instant testStartTime;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        business = new BusinessEntity("Salon Elite", "salon-elite", "UTC", "contact@salon.com", "+1234567890");
        business.setId(businessId);

        customer = new UserEntity(business, "Alice", "Smith", "alice@example.com", "hash", "+1234567891", UserRole.CUSTOMER);
        customer.setId(customerId);

        staff = new UserEntity(business, "Bob", "Barber", "bob@example.com", "hash", "+1234567892", UserRole.STAFF);
        staff.setId(staffId);

        service = new ServiceEntity(business, "Haircut & Styling", "Precision cut", 60, 15, new BigDecimal("65.00"), "USD");
        service.setId(serviceId);

        LocalDate bookingDate = LocalDate.now().plusDays(2);
        testStartTime = bookingDate.atTime(10, 0).atZone(ZoneId.of("UTC")).toInstant();
    }

    @Test
    @DisplayName("createReservation: Successfully books slot when available, publishes ReservationCreatedEvent")
    void createReservation_Success() {
        CreateReservationRequestDto request = new CreateReservationRequestDto(
                businessId, customerId, serviceId, staffId, testStartTime, "First time customer"
        );

        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));

        AvailableSlot availableSlot = new AvailableSlot(
                staffId,
                testStartTime,
                testStartTime.plus(Duration.ofMinutes(60)),
                ZonedDateTime.ofInstant(testStartTime, ZoneId.of("UTC")),
                ZonedDateTime.ofInstant(testStartTime.plus(Duration.ofMinutes(60)), ZoneId.of("UTC")),
                Duration.ofMinutes(60)
        );
        when(availabilityService.computeSlots(eq(businessId), eq(serviceId), eq(staffId), any(LocalDate.class)))
                .thenReturn(List.of(availableSlot));

        when(reservationRepository.saveAndFlush(any(ReservationEntity.class)))
                .thenAnswer(invocation -> {
                    ReservationEntity entity = invocation.getArgument(0);
                    entity.setId(UUID.randomUUID());
                    return entity;
                });

        ReservationResponseDto response = reservationService.createReservation(request);

        assertThat(response).isNotNull();
        assertThat(response.businessId()).isEqualTo(businessId);
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.staffId()).isEqualTo(staffId);
        assertThat(response.serviceId()).isEqualTo(serviceId);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.totalPrice()).isEqualTo(new BigDecimal("65.00"));
        assertThat(response.currency()).isEqualTo("USD");

        ArgumentCaptor<ReservationCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ReservationCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ReservationCreatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.reservationId()).isEqualTo(response.id());
        assertThat(publishedEvent.staffId()).isEqualTo(staffId);
        assertThat(publishedEvent.startTime()).isEqualTo(testStartTime);
    }

    @Test
    @DisplayName("createReservation: Throws SlotAlreadyBookedException when in-transaction check detects slot is unavailable")
    void createReservation_SlotUnavailable_ThrowsSlotAlreadyBookedException() {
        CreateReservationRequestDto request = new CreateReservationRequestDto(
                businessId, customerId, serviceId, staffId, testStartTime, null
        );

        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));

        when(availabilityService.computeSlots(eq(businessId), eq(serviceId), eq(staffId), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> reservationService.createReservation(request))
                .isInstanceOf(SlotAlreadyBookedException.class)
                .hasMessageContaining("no longer available");

        verify(reservationRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("createReservation: Catches DataIntegrityViolationException and rethrows SlotAlreadyBookedException (HTTP 409)")
    void createReservation_DataIntegrityViolation_ThrowsSlotAlreadyBookedException() {
        CreateReservationRequestDto request = new CreateReservationRequestDto(
                businessId, customerId, serviceId, staffId, testStartTime, null
        );

        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));

        AvailableSlot availableSlot = new AvailableSlot(
                staffId,
                testStartTime,
                testStartTime.plus(Duration.ofMinutes(60)),
                ZonedDateTime.ofInstant(testStartTime, ZoneId.of("UTC")),
                ZonedDateTime.ofInstant(testStartTime.plus(Duration.ofMinutes(60)), ZoneId.of("UTC")),
                Duration.ofMinutes(60)
        );
        when(availabilityService.computeSlots(eq(businessId), eq(serviceId), eq(staffId), any(LocalDate.class)))
                .thenReturn(List.of(availableSlot));

        when(reservationRepository.saveAndFlush(any(ReservationEntity.class)))
                .thenThrow(new DataIntegrityViolationException("ERROR: duplicate key value violates unique constraint \"uq_reservations_staff_active_slot\""));

        assertThatThrownBy(() -> reservationService.createReservation(request))
                .isInstanceOf(SlotAlreadyBookedException.class)
                .hasMessageContaining("already been booked");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("createReservation: Throws 404 AppException when business is not found")
    void createReservation_BusinessNotFound_ThrowsAppException() {
        CreateReservationRequestDto request = new CreateReservationRequestDto(
                businessId, customerId, serviceId, staffId, testStartTime, null
        );

        when(businessRepository.findById(businessId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.createReservation(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("cancelReservation: Successfully delegates to CancelReservationUseCase")
    void cancelReservation_Success() {
        UUID reservationId = UUID.randomUUID();
        ReservationEntity reservation = new ReservationEntity(
                business, customer, service, staff, testStartTime,
                testStartTime.plus(Duration.ofMinutes(60)), new BigDecimal("65.00"), "USD", null, null
        );
        reservation.setId(reservationId);
        reservation.setStatus(ReservationStatus.CANCELLED);

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        CancelReservationResult cancelResult = new CancelReservationResult(
                reservationId, "CANCELLED", true, false, false, BigDecimal.ZERO, null, "NONE", "User changed plans"
        );
        when(cancelReservationUseCase.execute(any(CancelReservationCommand.class))).thenReturn(cancelResult);

        ReservationResponseDto response = reservationService.cancelReservation(reservationId, customerId, "User changed plans");

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(cancelReservationUseCase).execute(any(CancelReservationCommand.class));
    }
}
