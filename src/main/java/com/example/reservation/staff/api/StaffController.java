package com.example.reservation.staff.api;

import com.example.reservation.staff.api.dto.CreateStaffRequestDto;
import com.example.reservation.staff.api.dto.StaffResponseDto;
import com.example.reservation.staff.service.StaffService;
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
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@Tag(name = "Staff Management", description = "Endpoints for staff members and service provider profiles")
public class StaffController {

    private final StaffService staffService;

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Register staff member", description = "Creates a staff profile linked to a user account")
    @ApiResponse(responseCode = "201", description = "Staff created")
    public ResponseEntity<StaffResponseDto> createStaff(@Valid @RequestBody CreateStaffRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.createStaff(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get staff profile by ID", description = "Retrieves staff details by UUID")
    @ApiResponse(responseCode = "200", description = "Staff profile found")
    @ApiResponse(responseCode = "404", description = "Staff profile not found")
    public ResponseEntity<StaffResponseDto> getStaffById(@PathVariable UUID id) {
        return ResponseEntity.ok(staffService.getStaffById(id));
    }

    @GetMapping
    @Operation(summary = "List active staff members", description = "Lists active staff providers for a business")
    @ApiResponse(responseCode = "200", description = "Staff list retrieved")
    public ResponseEntity<List<StaffResponseDto>> getActiveStaffByBusiness(@RequestParam UUID businessId) {
        return ResponseEntity.ok(staffService.getActiveStaffByBusiness(businessId));
    }
}
