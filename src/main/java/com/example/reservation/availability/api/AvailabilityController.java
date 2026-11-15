package com.example.reservation.availability.api;

import com.example.reservation.availability.api.dto.AvailabilityQueryDto;
import com.example.reservation.availability.api.dto.TimeSlotResponseDto;
import com.example.reservation.availability.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
@Tag(name = "Availability & Calendars", description = "Endpoints for discovering open time slots and provider schedules")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @PostMapping("/search")
    @Operation(summary = "Search open appointment slots", description = "Computes unreserved time slots for a service and date window")
    @ApiResponse(responseCode = "200", description = "Available slots computed")
    public ResponseEntity<List<TimeSlotResponseDto>> searchSlots(@Valid @RequestBody AvailabilityQueryDto query) {
        return ResponseEntity.ok(availabilityService.findAvailableSlots(query));
    }
}
