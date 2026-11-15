package com.example.reservation.availability.service;

import com.example.reservation.availability.api.dto.TimeSlotResponseDto;
import com.example.reservation.availability.domain.AvailableSlot;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AvailabilityService {
    List<TimeSlotResponseDto> getAvailableSlots(UUID businessId, UUID serviceId, UUID staffId, LocalDate date);
    List<AvailableSlot> computeSlots(UUID businessId, UUID serviceId, UUID staffId, LocalDate date);
}
