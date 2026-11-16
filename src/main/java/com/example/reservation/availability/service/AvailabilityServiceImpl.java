package com.example.reservation.availability.service;

import com.example.reservation.availability.api.dto.TimeSlotResponseDto;
import com.example.reservation.availability.domain.*;
import com.example.reservation.business.domain.BusinessEntity;
import com.example.reservation.business.repo.BusinessRepository;
import com.example.reservation.reservation.domain.ReservationEntity;
import com.example.reservation.reservation.domain.ReservationStatus;
import com.example.reservation.reservation.repo.ReservationRepository;
import com.example.reservation.service.domain.ServiceEntity;
import com.example.reservation.service.repo.ServiceRepository;
import com.example.reservation.shared.exception.AppException;
import com.example.reservation.shared.observability.ReservationMetrics;
import com.example.reservation.staff.domain.StaffTimeOffEntity;
import com.example.reservation.staff.domain.StaffWorkingHoursEntity;
import com.example.reservation.staff.repo.StaffTimeOffRepository;
import com.example.reservation.staff.repo.StaffWorkingHoursRepository;
import java.time.*;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityServiceImpl implements AvailabilityService {

    private final BusinessRepository businessRepository;
    private final ServiceRepository serviceRepository;
    private final StaffWorkingHoursRepository workingHoursRepository;
    private final StaffTimeOffRepository timeOffRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationMetrics reservationMetrics;

    @Override
    public List<TimeSlotResponseDto> getAvailableSlots(UUID businessId, UUID serviceId, UUID staffId, LocalDate date) {
        return reservationMetrics.recordAvailabilityLatency(businessId, () -> {
            List<AvailableSlot> slots = computeSlots(businessId, serviceId, staffId, date);

            return slots.stream()
                    .map(slot -> new TimeSlotResponseDto(
                            slot.staffId(),
                            slot.startInstant(),
                            slot.endInstant(),
                            slot.localStartTime(),
                            slot.localEndTime(),
                            slot.duration().toMinutes(),
                            true
                    ))
                    .toList();
        });
    }

    @Override
    public List<AvailableSlot> computeSlots(UUID businessId, UUID serviceId, UUID staffId, LocalDate date) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new AppException("Business not found", "BUSINESS_NOT_FOUND", HttpStatus.NOT_FOUND) {});

        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new AppException("Service offering not found", "SERVICE_NOT_FOUND", HttpStatus.NOT_FOUND) {});

        ZoneId tenantZone = ZoneId.of(business.getTimezone());
        ZonedDateTime startOfDay = date.atStartOfDay(tenantZone);
        ZonedDateTime endOfDay = date.plusDays(1).atStartOfDay(tenantZone);

        Instant dayStartUtc = startOfDay.toInstant();
        Instant dayEndUtc = endOfDay.toInstant();

        // 1. Fetch Working Hours for staff on that weekday
        List<StaffWorkingHoursEntity> workingHoursEntities = workingHoursRepository.findAllByStaffIdAndDayOfWeek(staffId, date.getDayOfWeek());
        List<WorkingHoursWindow> workingHours = workingHoursEntities.stream()
                .map(wh -> new WorkingHoursWindow(wh.getDayOfWeek(), wh.getStartTime(), wh.getEndTime()))
                .toList();

        // 2. Fetch Time-off blocks overlapping this date
        List<StaffTimeOffEntity> timeOffEntities = timeOffRepository.findOverlappingTimeOff(staffId, dayStartUtc, dayEndUtc);
        List<TimeInterval> timeOffBlocks = timeOffEntities.stream()
                .map(to -> new TimeInterval(to.getStartTime(), to.getEndTime()))
                .toList();

        // 3. Fetch existing reservations (PENDING or CONFIRMED)
        List<ReservationEntity> reservations = reservationRepository.findAllByStaffIdAndStartTimeBetween(staffId, dayStartUtc, dayEndUtc);
        List<TimeInterval> bookings = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING || r.getStatus() == ReservationStatus.CONFIRMED)
                .map(r -> new TimeInterval(r.getStartTime(), r.getEndTime()))
                .toList();

        Duration duration = Duration.ofMinutes(service.getDurationMinutes());
        Duration buffer = Duration.ofMinutes(service.getBufferMinutes());

        AvailabilityCalculationRequest request = new AvailabilityCalculationRequest(
                businessId,
                staffId,
                date,
                tenantZone,
                duration,
                buffer,
                duration.plus(buffer),
                workingHours,
                timeOffBlocks,
                bookings,
                Instant.now()
        );

        return AvailabilityEngine.computeAvailableSlots(request);
    }
}
