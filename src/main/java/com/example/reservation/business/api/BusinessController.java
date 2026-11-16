package com.example.reservation.business.api;

import com.example.reservation.business.api.dto.BusinessResponseDto;
import com.example.reservation.business.api.dto.CreateBusinessRequestDto;
import com.example.reservation.business.service.BusinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses")
@RequiredArgsConstructor
@Tag(name = "Business Tenants", description = "Endpoints for managing business tenant registrations and profiles")
public class BusinessController {

    private final BusinessService businessService;

    @PostMapping
    @Operation(summary = "Register business tenant", description = "Creates a new business profile and tenant workspace")
    @ApiResponse(responseCode = "201", description = "Business successfully created")
    @ApiResponse(responseCode = "409", description = "Business slug already exists")
    public ResponseEntity<BusinessResponseDto> createBusiness(@Valid @RequestBody CreateBusinessRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(businessService.createBusiness(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get business by ID", description = "Retrieves business details by UUID")
    @ApiResponse(responseCode = "200", description = "Business details retrieved")
    @ApiResponse(responseCode = "404", description = "Business not found")
    public ResponseEntity<BusinessResponseDto> getBusinessById(@PathVariable UUID id) {
        return ResponseEntity.ok(businessService.getBusinessById(id));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get business by vanity slug", description = "Retrieves business details by URL slug")
    @ApiResponse(responseCode = "200", description = "Business details retrieved")
    @ApiResponse(responseCode = "404", description = "Business not found")
    public ResponseEntity<BusinessResponseDto> getBusinessBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(businessService.getBusinessBySlug(slug));
    }
}
