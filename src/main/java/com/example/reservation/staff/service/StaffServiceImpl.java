package com.example.reservation.staff.service;

import com.example.reservation.staff.api.dto.CreateStaffRequestDto;
import com.example.reservation.staff.api.dto.StaffResponseDto;
import com.example.reservation.staff.repo.StaffRepository;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffServiceImpl implements StaffService {

    private final StaffRepository staffRepository;

    @Override
    @Transactional
    public StaffResponseDto createStaff(CreateStaffRequestDto request) {
        // Scaffold stub - business logic to be implemented
        return null;
    }

    @Override
    public StaffResponseDto getStaffById(UUID id) {
        // Scaffold stub - business logic to be implemented
        return null;
    }

    @Override
    public List<StaffResponseDto> getActiveStaffByBusiness(UUID businessId) {
        // Scaffold stub - business logic to be implemented
        return Collections.emptyList();
    }
}
