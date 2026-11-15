package com.example.reservation.service.service;

import com.example.reservation.service.api.dto.CreateServiceRequestDto;
import com.example.reservation.service.api.dto.ServiceResponseDto;
import com.example.reservation.service.repo.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceServiceImpl implements ServiceService {

    private final ServiceRepository serviceRepository;

    @Override
    @Transactional
    public ServiceResponseDto createService(CreateServiceRequestDto request) {
        // Scaffold stub - business logic to be implemented
        return null;
    }

    @Override
    public ServiceResponseDto getServiceById(UUID id) {
        // Scaffold stub - business logic to be implemented
        return null;
    }

    @Override
    public List<ServiceResponseDto> getActiveServices(UUID businessId) {
        // Scaffold stub - business logic to be implemented
        return Collections.emptyList();
    }
}
