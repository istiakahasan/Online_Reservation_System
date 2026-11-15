package com.example.reservation.business.service;

import com.example.reservation.business.api.dto.BusinessResponseDto;
import com.example.reservation.business.api.dto.CreateBusinessRequestDto;

import java.util.UUID;

public interface BusinessService {
    BusinessResponseDto createBusiness(CreateBusinessRequestDto request);
    BusinessResponseDto getBusinessById(UUID id);
    BusinessResponseDto getBusinessBySlug(String slug);
}

