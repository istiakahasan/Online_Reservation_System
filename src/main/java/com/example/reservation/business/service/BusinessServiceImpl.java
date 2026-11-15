package com.example.reservation.business.service;

import com.example.reservation.business.api.dto.BusinessResponseDto;
import com.example.reservation.business.api.dto.CreateBusinessRequestDto;
import com.example.reservation.business.repo.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessServiceImpl implements BusinessService {

    private final BusinessRepository businessRepository;

    @Override
    @Transactional
    public BusinessResponseDto createBusiness(CreateBusinessRequestDto request) {
        // Scaffold stub - business logic to be implemented
        return null;
    }

    @Override
    public BusinessResponseDto getBusinessById(UUID id) {
        // Scaffold stub - business logic to be implemented
        return null;
    }

    @Override
    public BusinessResponseDto getBusinessBySlug(String slug) {
        // Scaffold stub - business logic to be implemented
        return null;
    }
}
