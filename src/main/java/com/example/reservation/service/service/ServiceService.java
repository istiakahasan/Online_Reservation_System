package com.example.reservation.service.service;

import com.example.reservation.service.api.dto.CreateServiceRequestDto;
import com.example.reservation.service.api.dto.ServiceResponseDto;
import java.util.List;
import java.util.UUID;

public interface ServiceService {
    ServiceResponseDto createService(CreateServiceRequestDto request);
    ServiceResponseDto getServiceById(UUID id);
    List<ServiceResponseDto> getActiveServices(UUID businessId);
}
