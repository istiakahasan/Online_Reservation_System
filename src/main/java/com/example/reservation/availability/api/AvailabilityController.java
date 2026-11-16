package com.example.reservation.availability.api;

import com.example.reservation.availability.api.dto.TimeSlotResponseDto;
import com.example.reservation.availability.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/businesses/{businessId}/availability", "/api/v1/businesses/{businessId}/availability"})
@RequiredArgsConstructor
@Tag(name = "Availability", description = "Endpoints for finding open time slots for staff and services")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping
    @Operation(summary = "Get available appointment slots", description = "Derives open slots from working hours minus time-off minus active bookings")
    @ApiResponse(responseCode = "200", description = "Slots retrieved successfully")
    public ResponseEntity<List<TimeSlotResponseDto>> getAvailability(
            @PathVariable UUID businessId,
            @RequestParam UUID serviceId,
            @RequestParam UUID staffId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(availabilityService.getAvailableSlots(businessId, serviceId, staffId, date));
    }
}
