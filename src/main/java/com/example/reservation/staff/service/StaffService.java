package com.example.reservation.staff.service;

import com.example.reservation.staff.api.dto.CreateStaffRequestDto;
import com.example.reservation.staff.api.dto.StaffResponseDto;
import java.util.List;
import java.util.UUID;

public interface StaffService {
    StaffResponseDto createStaff(CreateStaffRequestDto request);
    StaffResponseDto getStaffById(UUID id);
    List<StaffResponseDto> getActiveStaffByBusiness(UUID businessId);
}
