package com.example.reservation.availability.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AvailabilityCalculationRequest(
        UUID businessId,
        UUID staffId,
        LocalDate date,
        ZoneId tenantZoneId,
        Duration serviceDuration,
        Duration bufferDuration,
        Duration slotStep,
        List<WorkingHoursWindow> workingHours,
        List<TimeInterval> timeOffBlocks,
        List<TimeInterval> existingBookings,
        Instant currentInstant
) {
    public AvailabilityCalculationRequest {
        Objects.requireNonNull(businessId, "businessId required");
        Objects.requireNonNull(staffId, "staffId required");
        Objects.requireNonNull(date, "date required");
        Objects.requireNonNull(tenantZoneId, "tenantZoneId required");
        Objects.requireNonNull(serviceDuration, "serviceDuration required");
        bufferDuration = bufferDuration != null ? bufferDuration : Duration.ZERO;
        slotStep = slotStep != null ? slotStep : serviceDuration.plus(bufferDuration);
        workingHours = workingHours != null ? workingHours : List.of();
        timeOffBlocks = timeOffBlocks != null ? timeOffBlocks : List.of();
        existingBookings = existingBookings != null ? existingBookings : List.of();
        currentInstant = currentInstant != null ? currentInstant : Instant.MIN;
    }
}
