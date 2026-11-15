package com.example.reservation.availability.service;

import com.example.reservation.availability.api.dto.AvailabilityQueryDto;
import com.example.reservation.availability.api.dto.TimeSlotResponseDto;
import com.example.reservation.availability.repo.AvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityServiceImpl implements AvailabilityService {

    private final AvailabilityRepository availabilityRepository;

    @Override
    public List<TimeSlotResponseDto> findAvailableSlots(AvailabilityQueryDto query) {
        // Scaffold stub - business logic to be implemented
        return Collections.emptyList();
    }
}
