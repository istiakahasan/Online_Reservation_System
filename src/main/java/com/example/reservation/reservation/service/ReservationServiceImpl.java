package com.example.reservation.reservation.service;

import com.example.reservation.availability.domain.AvailableSlot;
import com.example.reservation.availability.service.AvailabilityService;
import com.example.reservation.business.domain.BusinessEntity;
import com.example.reservation.business.repo.BusinessRepository;
import com.example.reservation.reservation.api.dto.CreateReservationRequestDto;
import com.example.reservation.reservation.api.dto.ReservationResponseDto;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.event.ReservationCreatedEvent;
import com.example.reservation.reservation.domain.exception.SlotAlreadyBookedException;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.service.domain.ServiceEntity;
import com.example.reservation.service.repo.ServiceRepository;
import com.example.reservation.shared.exception.AppException;
import com.example.reservation.shared.observability.ReservationMetrics;
import com.example.reservation.user.domain.UserEntity;
import com.example.reservation.user.repo.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationServiceImpl implements ReservationService {

    private static final Duration DEFAULT_HOLD_DURATION = Duration.ofMinutes(10);

    private final ReservationRepository reservationRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final AvailabilityService availabilityService;
    private final CancelReservationUseCase cancelReservationUseCase;
    private final ApplicationEventPublisher eventPublisher;
    private final ReservationMetrics reservationMetrics;

    @Override
    @Transactional
    public ReservationResponseDto createReservation(CreateReservationRequestDto request) {
        log.info("Initiating reservation creation: businessId={}, customerId={}, staffId={}, serviceId={}, startTime={}",
                request.businessId(), request.customerId(), request.staffId(), request.serviceId(), request.startTime());

        // 1. Validate request DTO entities
        BusinessEntity business = businessRepository.findById(request.businessId())
                .orElseThrow(() -> new AppException("Business not found with ID: " + request.businessId(),
                        "BUSINESS_NOT_FOUND", HttpStatus.NOT_FOUND) {});

        UserEntity customer = userRepository.findById(request.customerId())
                .orElseThrow(() -> new AppException("Customer not found with ID: " + request.customerId(),
                        "CUSTOMER_NOT_FOUND", HttpStatus.NOT_FOUND) {});

        UserEntity staff = userRepository.findById(request.staffId())
                .orElseThrow(() -> new AppException("Staff member not found with ID: " + request.staffId(),
                        "STAFF_NOT_FOUND", HttpStatus.NOT_FOUND) {});

        ServiceEntity service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new AppException("Service not found with ID: " + request.serviceId(),
                        "SERVICE_NOT_FOUND", HttpStatus.NOT_FOUND) {});

        if (!business.getId().equals(service.getBusiness().getId())) {
            throw new AppException("Service offering does not belong to the specified business",
                    "INVALID_BUSINESS_SERVICE", HttpStatus.BAD_REQUEST) {};
        }

        if (!business.getId().equals(staff.getBusiness().getId())) {
            throw new AppException("Staff member does not belong to the specified business",
                    "INVALID_BUSINESS_STAFF", HttpStatus.BAD_REQUEST) {};
        }

        // 2. Open transaction & Re-run availability check INSIDE the transaction
        ZoneId tenantZone = ZoneId.of(business.getTimezone());
        LocalDate bookingDate = request.startTime().atZone(tenantZone).toLocalDate();

        List<AvailableSlot> availableSlots = availabilityService.computeSlots(
                business.getId(),
                service.getId(),
                staff.getId(),
                bookingDate
        );

        boolean isSlotAvailable = availableSlots.stream()
                .anyMatch(slot -> slot.startInstant().equals(request.startTime()));

        if (!isSlotAvailable) {
            log.warn("In-transaction availability check failed: slot at {} is no longer free for staff {}",
                    request.startTime(), staff.getId());
            reservationMetrics.recordBookingConflict(request.businessId(), request.serviceId(), "SLOT_UNAVAILABLE");
            throw new SlotAlreadyBookedException(
                    "Requested slot at " + request.startTime() + " is no longer available"
            );
        }

        // 3. Construct Reservation with @Version (optimistic locking enabled via BaseAuditableEntity)
        Instant endTime = request.startTime().plus(Duration.ofMinutes(service.getDurationMinutes()));
        Instant holdExpiresAt = Instant.now().plus(DEFAULT_HOLD_DURATION);

        ReservationEntity reservation = new ReservationEntity(
                business,
                customer,
                service,
                staff,
                request.startTime(),
                endTime,
                service.getPriceAmount(),
                service.getCurrency(),
                holdExpiresAt,
                request.notes()
        );

        // 4. Insert reservation and rely on DB partial unique index as the last line of defense
        ReservationEntity savedReservation;
        try {
            savedReservation = reservationRepository.saveAndFlush(reservation);
            reservationMetrics.recordBookingSuccess(business.getId(), service.getId());
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent booking collision detected on DB unique index for staffId={} at startTime={}: {}",
                    staff.getId(), request.startTime(), ex.getMessage());
            reservationMetrics.recordBookingConflict(request.businessId(), request.serviceId(), "DB_UNIQUE_COLLISION");
            throw new SlotAlreadyBookedException(
                    "The requested slot at " + request.startTime() + " has already been booked by another transaction"
            );
        }

        // 5. Publish ReservationCreated event via ApplicationEventPublisher AFTER commit
        ReservationCreatedEvent event = new ReservationCreatedEvent(
                savedReservation.getId(),
                business.getId(),
                customer.getId(),
                service.getId(),
                staff.getId(),
                savedReservation.getStartTime(),
                savedReservation.getEndTime(),
                savedReservation.getTotalPrice(),
                savedReservation.getCurrency(),
                savedReservation.getHoldExpiresAt(),
                Instant.now()
        );
        eventPublisher.publishEvent(event);

        return mapToDto(savedReservation);
    }

    @Override
    public ReservationResponseDto getReservationById(UUID id) {
        return reservationRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new AppException("Reservation not found with ID: " + id,
                        "RESERVATION_NOT_FOUND", HttpStatus.NOT_FOUND) {});
    }

    @Override
    public List<ReservationResponseDto> getCustomerReservations(UUID customerId) {
        return reservationRepository.findAllByCustomerId(customerId).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public ReservationResponseDto cancelReservation(UUID id, UUID actorId, String reason) {
        UserEntity actor = userRepository.findById(actorId)
                .orElseThrow(() -> new AppException("Actor not found with ID: " + actorId,
                        "ACTOR_NOT_FOUND", HttpStatus.NOT_FOUND) {});

        CancelReservationCommand command = new CancelReservationCommand(
                id,
                actorId,
                actor.getRole(),
                reason != null ? reason : "Cancelled by user"
        );

        cancelReservationUseCase.execute(command);

        ReservationEntity updated = reservationRepository.findById(id)
                .orElseThrow(() -> new AppException("Reservation not found with ID: " + id,
                        "RESERVATION_NOT_FOUND", HttpStatus.NOT_FOUND) {});

        return mapToDto(updated);
    }

    private ReservationResponseDto mapToDto(ReservationEntity entity) {
        return new ReservationResponseDto(
                entity.getId(),
                entity.getBusiness().getId(),
                entity.getCustomer().getId(),
                entity.getService().getId(),
                entity.getStaff().getId(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getStatus().name(),
                entity.getTotalPrice(),
                entity.getCurrency(),
                entity.getHoldExpiresAt(),
                entity.getNotes()
        );
    }
}
