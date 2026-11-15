package com.example.reservation.availability.service;

import com.example.reservation.availability.api.dto.AvailabilityQueryDto;
import com.example.reservation.availability.api.dto.TimeSlotResponseDto;

import java.util.List;

public interface AvailabilityService {
    List<TimeSlotResponseDto> findAvailableSlots(AvailabilityQueryDto query);
}
