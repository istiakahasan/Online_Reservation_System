package com.example.reservation.service.api;

import com.example.reservation.service.api.dto.CreateServiceRequestDto;
import com.example.reservation.service.api.dto.ServiceResponseDto;
import com.example.reservation.service.service.ServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
@Tag(name = "Services Catalog", description = "Endpoints for managing bookable services offerings")
public class ServiceController {

    private final ServiceService serviceService;

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create bookable service", description = "Adds a new service offering to a business catalog")
    @ApiResponse(responseCode = "201", description = "Service created")
    public ResponseEntity<ServiceResponseDto> createService(@Valid @RequestBody CreateServiceRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceService.createService(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get service by ID", description = "Retrieves service details by UUID")
    @ApiResponse(responseCode = "200", description = "Service details found")
    @ApiResponse(responseCode = "404", description = "Service not found")
    public ResponseEntity<ServiceResponseDto> getServiceById(@PathVariable UUID id) {
        return ResponseEntity.ok(serviceService.getServiceById(id));
    }

    @GetMapping
    @Operation(summary = "List active services", description = "Retrieves all active offerings for a business tenant")
    @ApiResponse(responseCode = "200", description = "Active services retrieved")
    public ResponseEntity<List<ServiceResponseDto>> getActiveServices(@RequestParam UUID businessId) {
        return ResponseEntity.ok(serviceService.getActiveServices(businessId));
    }
}
